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
import searchengine.dto.SimpleResponse;
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
    public synchronized SimpleResponse startIndexing() {
        try {
            if (running) {
                log.warn("Запуск индексации отклонён: уже выполняется.");
                return new SimpleResponse(false, "Индексация уже запущена");
            }
            log.info("Запрошен запуск индексации.");
            running = true;
            executor = Executors.newFixedThreadPool(4);

            List<Site> sites = sitesList.getSites();
            if (sites == null || sites.isEmpty()) {
                log.warn("Список сайтов пуст — нечего индексировать.");
                return new SimpleResponse(false, "Список сайтов пуст");
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
            return new SimpleResponse(true, null);
        } catch (RuntimeException ex) {
            log.error("Ошибка при запуске индексации: {}", ex.getMessage(), ex);
            return new SimpleResponse(false, ex.getMessage());
        } catch (Exception ex) {
            log.error("Внутренняя ошибка при запуске индексации: {}", ex.getMessage(), ex);
            return new SimpleResponse(false, "Internal error: " + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public synchronized SimpleResponse stopIndexing() {
        try {
            if (!running) {
                log.info("Запрос остановки индексации — но индексация уже остановлена.");
                return new SimpleResponse(true, null);
            }
            log.info("Запрошена остановка индексации.");
            running = false;
            shutdownExecutor();
            updateIndexingSitesStatus();
            log.info("Индексация остановлена.");
            return new SimpleResponse(true, null);
        } catch (Exception ex) {
            log.error("Ошибка при остановке индексации: {}", ex.getMessage(), ex);
            return new SimpleResponse(false, "Internal error: " + ex.getMessage());
        }
    }

    private void shutdownExecutor() {
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
    }

    private void updateIndexingSitesStatus() {
        try {
            markIndexingSitesAsFailed();
            markIndexingSitesAsFailed();
        } catch (Exception ex) {
            log.warn("Не удалось обновить статусы сайтов после остановки индексации: {}", ex.getMessage());
        }
    }

    private void markIndexingSitesAsFailed() {
        List<SiteEntity> allSites = siteRepository.findAll();
        for (SiteEntity site : allSites) {
            if (site.getStatus() == SiteEntity.Status.INDEXING) {
                site.setStatus(SiteEntity.Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
        }
    }

    @Override
    public SimpleResponse indexPage(String url) {
        try {
            // Проверяем наличие сайта в конфигурации
            List<Site> sites = sitesList.getSites();
            if (sites == null || sites.isEmpty()) {
                throw new RuntimeException("Список сайтов в конфиге пуст");
            }
            
            String normalizedUrl = normalizeUrl(url);
            Site siteConfig = sites.stream()
                    .filter(s -> normalizedUrl.startsWith(normalizeUrl(s.getUrl())))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Сайт не найден в конфиге: " + url));
            
            // Находим или создаем SiteEntity в БД
            SiteEntity siteEntity = getOrCreateSiteEntity(siteConfig);
            siteEntity = saveSiteEntityWithRetry(siteEntity, siteConfig.getUrl());
            
            Set<String> visitedUrls = new HashSet<>();
            Queue<String> urlQueue = new LinkedList<>();
            visitedUrls.add(normalizedUrl);
            urlQueue.offer(normalizedUrl);
            
            while (!urlQueue.isEmpty()) {
                String currentUrl = urlQueue.poll();
                if (currentUrl == null) continue;
                crawlAndIndex(currentUrl, siteEntity, visitedUrls, urlQueue);
            }
            
            return new SimpleResponse(true, null);
        } catch (RuntimeException ex) {
            log.error("Ошибка при индексации страницы {}: {}", url, ex.getMessage(), ex);
            return new SimpleResponse(false, ex.getMessage());
        } catch (Exception ex) {
            log.error("Внутренняя ошибка при индексации страницы {}: {}", url, ex.getMessage(), ex);
            return new SimpleResponse(false, "Internal error: " + ex.getMessage());
        }
    }

    @Transactional
    protected void indexSite(Site siteConfig) {
        SiteEntity siteEntity = getOrCreateSiteEntity(siteConfig);
        siteEntity = saveSiteEntityWithRetry(siteEntity, siteConfig.getUrl());
        performSiteCrawling(siteConfig, siteEntity);
    }

    private SiteEntity getOrCreateSiteEntity(Site siteConfig) {
        Optional<SiteEntity> existing = siteRepository.findByUrl(siteConfig.getUrl());
        SiteEntity siteEntity;
        
        if (existing.isPresent()) {
            siteEntity = existing.get();
            siteEntity.setName(siteConfig.getName());
        } else {
            siteEntity = new SiteEntity();
            siteEntity.setUrl(siteConfig.getUrl());
            siteEntity.setName(siteConfig.getName());
        }
        
        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        
        return siteEntity;
    }

    private SiteEntity saveSiteEntityWithRetry(SiteEntity siteEntity, String url) {
        try {
            return siteRepository.save(siteEntity);
        } catch (DataIntegrityViolationException ex) {
            log.warn("DataIntegrityViolation при сохранении SiteEntity для URL {}: {}. Попытка повторного чтения.", url, ex.getMessage());
            Optional<SiteEntity> re = siteRepository.findByUrl(url);
            if (re.isPresent()) {
                siteEntity = re.get();
                siteEntity.setStatus(SiteEntity.Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                return siteRepository.save(siteEntity);
            } else {
                throw ex;
            }
        }
    }

    private void performSiteCrawling(Site siteConfig, SiteEntity siteEntity) {
        try {
            crawlSite(siteConfig.getUrl(), siteEntity);
            updateSiteStatusAfterCrawling(siteEntity);
        } catch (Exception e) {
            log.error("Ошибка при индексации сайта {}: {}", siteConfig.getUrl(), e.toString(), e);
            markSiteAsFailed(siteEntity, e.getMessage());
        }
    }

    private void updateSiteStatusAfterCrawling(SiteEntity siteEntity) {
        if (!running) {
            markSiteAsFailed(siteEntity, "Индексация остановлена пользователем");
        } else {
            markSiteAsIndexed(siteEntity);
        }
    }

    private void markSiteAsFailed(SiteEntity siteEntity, String error) {
        siteEntity.setStatus(SiteEntity.Status.FAILED);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private void markSiteAsIndexed(SiteEntity siteEntity) {
        siteEntity.setStatus(SiteEntity.Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setLastError(null);
        siteRepository.save(siteEntity);
    }


    private void crawlSite(String startUrl, SiteEntity site) {
        Set<String> visitedUrls = new HashSet<>();
        Queue<String> urlQueue = new LinkedList<>();
        String normalizedStartUrl = normalizeUrl(startUrl);
        urlQueue.offer(normalizedStartUrl);
        visitedUrls.add(normalizedStartUrl);
        log.info("=== НАЧАЛО ИНДЕКСАЦИИ САЙТА: {} ===", site.getUrl());
        log.info("Стартовый URL: {} (нормализованный: {})", startUrl, normalizedStartUrl);

        int processedCount = 0;
        while (!urlQueue.isEmpty() && running) {
            String url = urlQueue.poll();
            if (url == null) {
                log.warn("Получен null из очереди, пропускаем");
                continue;
            }

            processedCount++;
            log.info(">>> Обработка страницы #{}, URL: {}, в очереди осталось: {}", processedCount, url, urlQueue.size());

            try {
                crawlAndIndex(url, site, visitedUrls, urlQueue);
                log.info("<<< Страница #{} обработана, размер очереди: {}", processedCount, urlQueue.size());
            } catch (Exception e) {
                log.error("ОШИБКА при обработке URL {}: {}", url, e.getMessage(), e);
            }
        }
        
        if (!running) {
            log.warn("Индексация остановлена пользователем. Обработано страниц: {}", processedCount);
        } else {
            log.info("=== ИНДЕКСАЦИЯ САЙТА {} ЗАВЕРШЕНА. Обработано страниц: {} ===", site.getUrl(), processedCount);
        }
    }

    private void crawlAndIndex(String url, SiteEntity site, Set<String> visitedUrls, Queue<String> urlQueue) {
        if (!running) {
            log.debug("Индексация остановлена — пропускаю: {}", url);
            return;
        }

        String path = extractPath(url, site);
        log.info("Crawling start: {}", url);

        try {
            Document doc = fetchDocument(url);
            String text = extractText(doc);
            PageEntity page = saveOrUpdatePage(site, path, text);
            indexPageContent(page, site, text);
            crawlLinks(doc, site, visitedUrls, urlQueue);
        } catch (IOException e) {
            handleCrawlError(site, url, "Ошибка чтения " + url + ": " + e.getMessage(), e);
        } catch (Exception e) {
            handleCrawlError(site, url, "Unexpected error while crawling " + url + ": " + e.getMessage(), e);
        }
    }

    private String extractPath(String url, SiteEntity site) {
        String normalizedSiteUrl = normalizeUrl(site.getUrl());
        String normalizedUrl = normalizeUrl(url);
        String path = normalizedUrl.replaceFirst("^" + Pattern.quote(normalizedSiteUrl), "");
        // Если path пустой, это корневая страница
        return path.isEmpty() ? "/" : path;
    }

    private Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SearchEngineBot/1.0)")
                .timeout(10_000)
                .get();
    }

    private String extractText(Document doc) {
        return doc.body() != null ? doc.body().text() : "";
    }

    private PageEntity saveOrUpdatePage(SiteEntity site, String path, String text) {
        Optional<PageEntity> existingOpt = pageRepository.findBySiteAndPath(site, path);
        
        if (existingOpt.isPresent()) {
            return updateExistingPage(existingOpt.get(), text, site, path);
        } else {
            return createNewPage(site, path, text);
        }
    }

    private PageEntity updateExistingPage(PageEntity page, String text, SiteEntity site, String path) {
        page.setCode(200);
        page.setContent(text);
        page = pageRepository.save(page);
        log.info("Updated page id={} site={} path={}", page.getId(), site.getUrl(), page.getPath());
        
        deleteOldIndices(page);
        return page;
    }

    private void deleteOldIndices(PageEntity page) {
        try {
            indexRepository.deleteByPage(page);
            log.debug("Deleted old indices for page id={}", page.getId());
        } catch (Exception ex) {
            log.warn("Не удалось удалить старые индексы для page id={}: {}", page.getId(), ex.getMessage());
        }
    }

    private PageEntity createNewPage(SiteEntity site, String path, String text) {
        PageEntity page = new PageEntity();
        page.setSite(site);
        page.setPath(path);
        page.setCode(200);
        page.setContent(text);
        page = pageRepository.save(page);
        log.info("Saved page id={} site={} path={}", page.getId(), site.getUrl(), page.getPath());
        return page;
    }

    private void indexPageContent(PageEntity page, SiteEntity site, String text) {
        List<String> lemmas = morphologyService.lemmatize(text);
        Map<String, Integer> freq = countLemmaFrequency(lemmas);
        saveIndices(page, site, freq);
    }

    private Map<String, Integer> countLemmaFrequency(List<String> lemmas) {
        Map<String, Integer> freq = new HashMap<>();
        for (String lemma : lemmas) {
            if (lemma == null || lemma.isBlank()) continue;
            freq.merge(lemma, 1, Integer::sum);
        }
        return freq;
    }

    private void saveIndices(PageEntity page, SiteEntity site, Map<String, Integer> freq) {
        for (var entry : freq.entrySet()) {
            String lemma = entry.getKey();
            int count = entry.getValue();
            
            LemmaEntity lemmaEntity = getOrCreateLemma(site, lemma);
            lemmaEntity.setFrequency(lemmaEntity.getFrequency() + count);
            lemmaEntity = lemmaRepository.save(lemmaEntity);
            
            createIndexEntry(page, lemmaEntity, count);
        }
    }

    private LemmaEntity getOrCreateLemma(SiteEntity site, String lemma) {
        return lemmaRepository
                .findBySiteAndLemma(site, lemma)
                .orElseGet(() -> {
                    LemmaEntity e = new LemmaEntity();
                    e.setSite(site);
                    e.setLemma(lemma);
                    e.setFrequency(0);
                    return e;
                });
    }

    private void createIndexEntry(PageEntity page, LemmaEntity lemmaEntity, int rank) {
        IndexEntity idx = new IndexEntity();
        idx.setPage(page);
        idx.setLemma(lemmaEntity);
        idx.setRank(rank);
        indexRepository.save(idx);
    }

    private void crawlLinks(Document doc, SiteEntity site, Set<String> visitedUrls, Queue<String> urlQueue) {
        Elements links = doc.select("a[href]");
        int linksFound = 0;
        int linksAdded = 0;
        
        String siteUrlOriginal = site.getUrl();
        String siteUrlNormalized = normalizeUrl(siteUrlOriginal);
        
        log.info("Поиск ссылок на странице. Базовый URL сайта: {}", siteUrlOriginal);
        
        for (Element link : links) {
            if (!running) break;
            
            String href = link.attr("href");
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:") || href.equals("#")) {
                continue;
            }
            
            String absUrl = link.absUrl("href");
            if (absUrl.isEmpty() || absUrl.equals("#")) {
                continue;
            }
            
            linksFound++;
            
            String normalizedUrl = normalizeUrl(absUrl);
            
            if (!normalizedUrl.startsWith(siteUrlNormalized)) {
                log.debug("Пропущена внешняя ссылка: {} (базовый URL: {})", normalizedUrl, siteUrlNormalized);
                continue;
            }
            
            if (!visitedUrls.contains(normalizedUrl)) {
                visitedUrls.add(normalizedUrl);
                urlQueue.offer(normalizedUrl);
                linksAdded++;
                log.info("✓ Добавлен в очередь: {} (всего найдено: {}, добавлено: {})", normalizedUrl, linksFound, linksAdded);
            } else {
                log.debug("Пропущен уже посещенный URL: {}", normalizedUrl);
            }
        }
        log.info("Итого на странице: найдено ссылок {}, добавлено новых {}, размер очереди: {}", 
                 linksFound, linksAdded, urlQueue.size());
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        int anchorIndex = url.indexOf('#');
        if (anchorIndex != -1) {
            url = url.substring(0, anchorIndex);
        }
        
        url = url.replaceFirst("^https://www\\.", "https://");
        url = url.replaceFirst("^http://www\\.", "http://");
        
        if (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        
        return url;
    }

    private void handleCrawlError(SiteEntity site, String url, String errorMessage, Exception e) {
        if (e instanceof IOException) {
            log.warn("Ошибка чтения {}: {}", url, e.getMessage());
        } else {
            log.error("Unexpected error while crawling {}: {}", url, e.toString(), e);
        }
        site.setLastError(errorMessage);
        siteRepository.save(site);
    }

    @Override
    public boolean isIndexing() {
        return running;
    }
}
