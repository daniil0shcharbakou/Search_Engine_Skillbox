package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final MorphologyService morphologyService;

    private volatile boolean running = false;
    private ExecutorService executor;

    @Override
    public synchronized void startIndexing() {
        if (running) {
            log.warn("Запуск индексации отклонён: уже выполняется.");
            throw new RuntimeException("Индексация уже запущена");
        }
        log.info("Запрошен запуск индексации.");
        running = true;
        // фиксированный пул; можно вынести в конфиг
        executor = Executors.newFixedThreadPool(4);

        List<Site> sites = sitesList.getSites();
        if (sites == null || sites.isEmpty()) {
            log.warn("Список сайтов пуст — нечего индексировать.");
            return;
        }

        for (Site siteConfig : sites) {
            executor.submit(() -> {
                try {
                    log.info("Запуск индексации для сайта: {}", siteConfig.getUrl());
                    indexSite(siteConfig);
                    log.info("Индексация для сайта {} завершена.", siteConfig.getUrl());
                } catch (Exception ex) {
                    log.error("Ошибка при индексации сайта {}: {}", siteConfig.getUrl(), ex.toString(), ex);
                }
            });
        }
    }

    @Override
    public synchronized void stopIndexing() {
        if (!running) {
            log.info("Запрос остановки индексации — но индексация уже остановлена.");
            return;
        }
        log.info("Запрошена остановка индексации.");
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Пул потоков не завершился за 5 секунд, возможны незавершённые задачи.");
                } else {
                    log.info("Пул потоков корректно завершён.");
                }
            } catch (InterruptedException e) {
                log.warn("Ожидание завершения пула прервано.", e);
                Thread.currentThread().interrupt();
            }
        }
        log.info("Индексация остановлена.");
    }

    @Override
    public void indexPage(String url) {
        // Ищем сайт, к которому принадлежит URL (по конфигу в БД)
        SiteEntity siteEntity = siteRepository.findAll().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Сайт не найден в конфиге: " + url));
        // Выполняем синхронный обход и индексацию одной страницы
        crawlAndIndex(url, siteEntity);
    }

    /**
     * Индексация одного сайта (создаём запись SiteEntity и начинаем обход).
     */
    @Transactional
    protected void indexSite(Site siteConfig) {
        // Пытаемся найти уже существующую запись по URL
        Optional<SiteEntity> existing = siteRepository.findByUrl(siteConfig.getUrl());

        SiteEntity siteEntity;
        if (existing.isPresent()) {
            siteEntity = existing.get();
            // Обновляем имя (если изменилось)
            siteEntity.setName(siteConfig.getName());
        } else {
            siteEntity = new SiteEntity();
            siteEntity.setUrl(siteConfig.getUrl());
            siteEntity.setName(siteConfig.getName());
        }

        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());

        try {
            siteEntity = siteRepository.save(siteEntity);
        } catch (DataIntegrityViolationException ex) {
            // В редком случае конкурентной вставки — снова прочитаем запись и используем её
            log.warn("DataIntegrityViolation при сохранении SiteEntity для URL {}: {}. Попытка повторного чтения.", siteConfig.getUrl(), ex.getMessage());
            Optional<SiteEntity> re = siteRepository.findByUrl(siteConfig.getUrl());
            if (re.isPresent()) {
                siteEntity = re.get();
                siteEntity.setStatus(SiteEntity.Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteEntity = siteRepository.save(siteEntity);
            } else {
                // если всё же не удалось — пробрасываем
                throw ex;
            }
        }

        try {
            // Запускаем полный обход с корня
            crawlAndIndex(siteConfig.getUrl(), siteEntity);
            // Если дошли до конца успешно — помечаем как INDEXED
            siteEntity.setStatus(SiteEntity.Status.INDEXED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(null);
            siteRepository.save(siteEntity);
        } catch (Exception e) {
            log.error("Ошибка при индексации сайта {}: {}", siteConfig.getUrl(), e.toString(), e);
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(e.getMessage());
            siteRepository.save(siteEntity);
        }
    }

    /**
     * Рекурсивный обход и индексация одной страницы.
     *
     * @param url  полный URL страницы
     * @param site сущность сайта (SiteEntity)
     */
    private void crawlAndIndex(String url, SiteEntity site) {
        if (!running) {
            log.debug("Индексация остановлена — пропускаю: {}", url);
            return;
        }

        // формируем путь относительно корня сайта
        String path = url.replaceFirst("^" + Pattern.quote(site.getUrl()), "");
        log.info("Crawling start: {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; SearchEngineBot/1.0)")
                    .timeout(10_000)
                    .get();
            String text = doc.body() != null ? doc.body().text() : "";

            // Найдём существующую страницу (если есть) — иначе создадим новую
            Optional<PageEntity> existingOpt = pageRepository.findBySiteAndPath(site, path);
            PageEntity page;
            if (existingOpt.isPresent()) {
                page = existingOpt.get();
                page.setCode(200);
                page.setContent(text);
                page = pageRepository.save(page);
                log.info("Updated page id={} site={} path={}", page.getId(), site.getUrl(), page.getPath());

                // удалить прежние индексы для этой страницы, чтобы пересоздать актуальные
                try {
                    indexRepository.deleteByPage(page);
                    log.debug("Deleted old indices for page id={}", page.getId());
                } catch (Exception ex) {
                    log.warn("Не удалось удалить старые индексы для page id={}: {}", page.getId(), ex.getMessage());
                }
            } else {
                page = new PageEntity();
                page.setSite(site);
                page.setPath(path);
                page.setCode(200);
                page.setContent(text);
                page = pageRepository.save(page);
                log.info("Saved page id={} site={} path={}", page.getId(), site.getUrl(), page.getPath());
            }

            // Лемматизация и подсчёт частот
            List<String> lemmas = morphologyService.lemmatize(text);
            Map<String, Integer> freq = new HashMap<>();
            for (String lemma : lemmas) {
                if (lemma == null || lemma.isBlank()) continue;
                freq.merge(lemma, 1, Integer::sum);
            }

            // сохраняем или обновляем леммы и создаём связи (IndexEntity)
            for (var entry : freq.entrySet()) {
                String lemma = entry.getKey();
                int count = entry.getValue();

                LemmaEntity lemmaEntity = lemmaRepository
                        .findBySiteAndLemma(site, lemma)
                        .orElseGet(() -> {
                            LemmaEntity e = new LemmaEntity();
                            e.setSite(site);
                            e.setLemma(lemma);
                            e.setFrequency(0);
                            return e;
                        });
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + count);
                lemmaEntity = lemmaRepository.save(lemmaEntity);

                IndexEntity idx = new IndexEntity();
                idx.setPage(page);
                idx.setLemma(lemmaEntity);
                idx.setRank(count);
                indexRepository.save(idx);
            }

            // рекурсивно обходим все внутренние ссылки
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absUrl = link.absUrl("href");
                // проверяем, что ссылка внутри сайта и что индексация всё ещё разрешена
                if (absUrl.startsWith(site.getUrl()) && running) {
                    // избегаем бесконечных циклов и дублей — проверим, не проиндексирована ли уже
                    String childPath = absUrl.replaceFirst("^" + Pattern.quote(site.getUrl()), "");
                    if (!pageRepository.existsBySiteAndPath(site, childPath)) {
                        // рекурсивно индексируем
                        crawlAndIndex(absUrl, site);
                    }
                }
            }
        } catch (IOException e) {
            // сохраняем ошибку сайта (последний error) и не прерываем весь процесс
            log.warn("Ошибка чтения {}: {}", url, e.getMessage());
            site.setLastError("Ошибка чтения " + url + ": " + e.getMessage());
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("Unexpected error while crawling {}: {}", url, e.toString(), e);
            site.setLastError("Unexpected error while crawling " + url + ": " + e.getMessage());
            siteRepository.save(site);
        }
    }

    @Override
    public boolean isIndexing() {
        return running;
    }
}
