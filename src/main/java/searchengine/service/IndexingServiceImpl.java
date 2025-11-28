package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.SimpleResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.utils.PageIndexingUtils;
import searchengine.utils.UrlUtils;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageIndexingUtils pageIndexingUtils;

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
            Site siteConfig = findSiteConfig(url);
            String name = UrlUtils.extractSiteName(url);
            sitesList.addSiteIfNotExists(url, name);
            List<Site> sites = sitesList.getSites();
            SiteEntity siteEntity = getOrCreateSiteEntity(siteConfig);
            siteEntity = saveSiteEntityWithRetry(siteEntity, siteConfig.getUrl());
            processPageIndexing(url, siteEntity);
            return new SimpleResponse(true, null);
        } catch (RuntimeException ex) {
            log.error("Ошибка при индексации страницы {}: {}", url, ex.getMessage(), ex);
            return new SimpleResponse(false, ex.getMessage());
        } catch (Exception ex) {
            log.error("Внутренняя ошибка при индексации страницы {}: {}", url, ex.getMessage(), ex);
            return new SimpleResponse(false, "Internal error: " + ex.getMessage());
        }
    }

    private Site findSiteConfig(String url) {
        List<Site> sites = sitesList.getSites();
        if (sites == null || sites.isEmpty()) {
            throw new RuntimeException("Список сайтов в конфиге пуст");
        }
        String normalizedUrl = UrlUtils.normalizeUrl(url);
        return sites.stream()
                .filter(s -> normalizedUrl.startsWith(UrlUtils.normalizeUrl(s.getUrl())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Сайт не найден в конфиге: " + url));
    }

    private void prepareSiteConfig(String url, Site siteConfig) {
        String name = UrlUtils.extractSiteName(url);
        sitesList.addSiteIfNotExists(url, name);
        if (siteConfig.getUrl() != url) {
            siteConfig.setUrl(url);
        }
    }

    private void processPageIndexing(String url, SiteEntity siteEntity) {
        String normalizedUrl = UrlUtils.normalizeUrl(url);
        Set<String> visitedUrls = new HashSet<>();
        Queue<String> urlQueue = new LinkedList<>();
        visitedUrls.add(normalizedUrl);
        urlQueue.offer(normalizedUrl);
        
        while (!urlQueue.isEmpty()) {
            String currentUrl = urlQueue.poll();
            if (currentUrl == null) continue;
            crawlAndIndex(currentUrl, siteEntity, visitedUrls, urlQueue);
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
        String normalizedStartUrl = UrlUtils.normalizeUrl(startUrl);
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

        String path = UrlUtils.extractPath(url, site);
        log.info("Crawling start: {}", url);

        try {
            Document doc = pageIndexingUtils.fetchDocument(url);
            String text = pageIndexingUtils.extractText(doc);
            PageEntity page = pageIndexingUtils.saveOrUpdatePage(site, path, text);
            pageIndexingUtils.indexPageContent(page, site, text);
            Elements links = doc.select("a[href]");
            UrlUtils.crawlLinks(links, site, visitedUrls, urlQueue, running);
        } catch (IOException e) {
            handleCrawlError(site, url, "Ошибка чтения " + url + ": " + e.getMessage(), e);
        } catch (Exception e) {
            handleCrawlError(site, url, "Unexpected error while crawling " + url + ": " + e.getMessage(), e);
        }
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
