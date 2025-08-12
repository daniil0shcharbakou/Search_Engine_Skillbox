package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.model.*;
import searchengine.repository.*;

import javax.transaction.Transactional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final MorphologyService morphologyService;
    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);

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
        executor = Executors.newFixedThreadPool(4);

        for (Site siteConfig : sitesList.getSites()) {
            // submit каждому сайту задачу индексирования
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
                log.warn("Ожидание завершения пулa прервано.", e);
                Thread.currentThread().interrupt();
            }
        }
        log.info("Индексация остановлена.");
    }


    @Override
    public void indexPage(String url) {
        var siteEntity = siteRepository.findAll().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Сайт не найден в конфиге"));
        crawlAndIndex(url, siteEntity);
    }

    @Transactional
    protected void indexSite(Site siteConfig) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(siteConfig.getUrl());
        siteEntity.setName(siteConfig.getName());
        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity = siteRepository.save(siteEntity);

        try {
            crawlAndIndex(siteConfig.getUrl(), siteEntity);
            siteEntity.setStatus(SiteEntity.Status.INDEXED);
        } catch (Exception e) {
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setLastError(e.getMessage());
        } finally {
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
    }

    private void crawlAndIndex(String url, SiteEntity site) {
        if (!running) {
            log.debug("Индексация остановлена — пропускаю: {}", url);
            return;
        }
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
                indexRepository.deleteByPage(page);
                log.debug("Deleted old indices for page id={}", page.getId());
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
                freq.merge(lemma, 1, Integer::sum);
            }
            log.info("Lemmas extracted for page id={} : tokens={} unique={}", page.getId(), lemmas.size(), freq.size());

            // Сохраняем леммы и создаём индексные записи
            for (var entry : freq.entrySet()) {
                String lemmaStr = entry.getKey();
                int count = entry.getValue();

                LemmaEntity lemmaEntity = lemmaRepository
                        .findBySiteAndLemma(site, lemmaStr)
                        .orElseGet(() -> {
                            LemmaEntity e = new LemmaEntity();
                            e.setSite(site);
                            e.setLemma(lemmaStr);
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

            // Рекурсивный обход ссылок (по внутренним)
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absUrl = link.absUrl("href");
                if (absUrl.startsWith(site.getUrl()) && running) {
                    crawlAndIndex(absUrl, site);
                }
            }

            log.info("Crawling finished: {} (page id={})", url, page.getId());
        } catch (IOException e) {
            log.error("Ошибка при чтении {} : {}", url, e.getMessage());
            site.setLastError("Ошибка чтения " + url + ": " + e.getMessage());
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("Unexpected error while crawling {}: {}", url, e.toString(), e);
            site.setLastError("Unexpected error: " + e.getMessage());
            siteRepository.save(site);
        }
    }
}

