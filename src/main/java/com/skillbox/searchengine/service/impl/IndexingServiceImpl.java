package com.skillbox.searchengine.service.impl;

import com.skillbox.searchengine.config.IndexingSettings;
import com.skillbox.searchengine.model.*;
import com.skillbox.searchengine.model.enums.SiteStatus;
import com.skillbox.searchengine.repository.*;
import com.skillbox.searchengine.service.IndexingService;
import com.skillbox.searchengine.util.HtmlUtils;
import com.skillbox searchengine.util.LemmaUtils;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final IndexingSettings settings;

    @Override
    public void startIndexing() {
        for (var cfg : settings.getSites()) {
            Site site = new Site();
            site.setName(cfg.getName());
            site.setUrl(cfg.getUrl());
            site.setStatus(SiteStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepo.save(site);
            ForkJoinPool.commonPool().submit(() -> crawlPage(site, cfg.getUrl()));
        }
    }

    private void crawlPage(Site site, String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(settings.getUserAgent())
                    .get();
            String html = doc.html();
            Page page = new Page();
            page.setSite(site);
            page.setPath(url);
            page.setCode(200);
            page.setContent(html);
            pageRepo.save(page);
            String text = HtmlUtils.cleanText(html);
            var lemmaCounts = LemmaUtils.getLemmaCounts(text);
            for (var entry : lemmaCounts.entrySet()) {
                var optional = lemmaRepo.findByLemmaAndSiteId(entry.getKey(), site.getId());
                Lemma lemma = optional.orElseGet(() -> {
                    Lemma l = new Lemma();
                    l.setLemma(entry.getKey());
                    l.setFrequency(0);
                    l.setSite(site);
                    return l;
                });
                lemma.setFrequency(lemma.getFrequency() + entry.getValue());
                lemmaRepo.save(lemma);
                IndexEntry idx = new IndexEntry();
                idx.setPage(page);
                idx.setLemma(lemma);
                idx.setRank(entry.getValue());
                indexRepo.save(idx);
            }
            site.setStatus(SiteStatus.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepo.save(site);
        } catch (Exception e) {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepo.save(site);
        }
    }
}
