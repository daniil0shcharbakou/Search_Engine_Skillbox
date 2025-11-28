package searchengine.utils;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.model.SiteEntity;

import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

public class UrlUtils {
    private static final Logger log = LoggerFactory.getLogger(UrlUtils.class);

    public static String normalizeUrl(String url) {
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

    public static String extractPath(String url, SiteEntity site) {
        String normalizedSiteUrl = normalizeUrl(site.getUrl());
        String normalizedUrl = normalizeUrl(url);
        String path = normalizedUrl.replaceFirst("^" + Pattern.quote(normalizedSiteUrl), "");
        return path.isEmpty() ? "/" : path;
    }

    public static String extractSiteName(String baseUrl) {
        try {
            java.net.URL urlObj = new java.net.URL(baseUrl);
            String host = urlObj.getHost();
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String[] parts = host.split("\\.");
            if (parts.length > 0) {
                String name = parts[0];
                return name.substring(0, 1).toUpperCase() + name.substring(1);
            }
            return host;
        } catch (Exception e) {
            log.warn("Не удалось извлечь имя сайта из {}: {}", baseUrl, e.getMessage());
            return "Unknown";
        }
    }

    public static void crawlLinks(Elements links, SiteEntity site, Set<String> visitedUrls, 
                                  Queue<String> urlQueue, boolean running) {
        int linksFound = 0;
        int linksAdded = 0;
        
        String siteUrlOriginal = site.getUrl();
        String siteUrlNormalized = normalizeUrl(siteUrlOriginal);
        
        log.info("Поиск ссылок на странице. Базовый URL сайта: {}", siteUrlOriginal);
        
        for (Element link : links) {
            if (!running) break;
            
            String href = link.attr("href");
            if (href.isEmpty() || href.startsWith("javascript:") || 
                href.startsWith("mailto:") || href.equals("#")) {
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
                log.info("✓ Добавлен в очередь: {} (всего найдено: {}, добавлено: {})", 
                        normalizedUrl, linksFound, linksAdded);
            } else {
                log.debug("Пропущен уже посещенный URL: {}", normalizedUrl);
            }
        }
        log.info("Итого на странице: найдено ссылок {}, добавлено новых {}, размер очереди: {}", 
                 linksFound, linksAdded, urlQueue.size());
    }
}

