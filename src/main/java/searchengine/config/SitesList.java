package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<Site> sites;

    public synchronized void addSiteIfNotExists(String url, String name) {
        if (sites == null) {
            sites = new CopyOnWriteArrayList<>();
        } else if (!(sites instanceof CopyOnWriteArrayList)) {
            sites = new CopyOnWriteArrayList<>(sites);
        }
        
        String normalizedUrl = normalizeUrl(url);
        boolean exists = sites.stream()
                .anyMatch(s -> normalizeUrl(s.getUrl()).equals(normalizedUrl));
        
        if (!exists) {
            Site newSite = new Site();
            newSite.setUrl(normalizedUrl);
            newSite.setName(name);
            sites.add(newSite);
        }
        int x = 0;
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
}
