package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.service.MorphologyService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PageIndexingUtils {
    private static final Logger log = LoggerFactory.getLogger(PageIndexingUtils.class);

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final MorphologyService morphologyService;

    public Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SearchEngineBot/1.0)")
                .timeout(10_000)
                .get();
    }

    public String extractText(Document doc) {
        return doc.body() != null ? doc.body().text() : "";
    }

    public PageEntity saveOrUpdatePage(SiteEntity site, String path, String text) {
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

    public void indexPageContent(PageEntity page, SiteEntity site, String text) {
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
}

