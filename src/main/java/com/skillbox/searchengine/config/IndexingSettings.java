package com.skillbox.searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class IndexingSettings {
    private List<SiteConfig> sites;
    private String userAgent;

    @Data
    public static class SiteConfig {
        private String url;
        private String name;
    }
}
