package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final MorphologyService morphologyService;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SnippetService snippetService;

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String site, int offset, int limit) {
        try {
            if (query == null || query.isBlank()) {
                return new SearchResponse(false, 0, Collections.emptyList());
            }

            List<String> lemmas = extractLemmas(query);
            if (lemmas.isEmpty()) {
                return new SearchResponse(true, 0, Collections.emptyList());
            }

            Map<String, Long> dfMap = buildDfMap(lemmas, site);
            long totalPages = countTotalPages(site);
            if (totalPages <= 0) {
                return new SearchResponse(true, 0, Collections.emptyList());
            }

            Map<Integer, Map<String, Double>> pageLemmaTf = buildPageLemmaTfMap(lemmas, site);
            if (pageLemmaTf.isEmpty()) {
                return new SearchResponse(true, 0, Collections.emptyList());
            }

            Map<String, Double> idfMap = calculateIdfMap(lemmas, dfMap, totalPages);
            List<PageScore> pageScores = calculatePageScores(pageLemmaTf, idfMap);
            pageScores.sort((a, b) -> Float.compare(b.score, a.score));

            int total = pageScores.size();
            if (total == 0) {
                return new SearchResponse(true, 0, Collections.emptyList());
            }

            List<PageScore> pageScoresPage = getPageScoresPage(pageScores, offset, limit);
            List<SearchItem> items = buildSearchItems(pageScoresPage, query, lemmas);

            items.forEach(item -> {
                if (item != null) {
                    item.setUri("");
                }
            });

            return new SearchResponse(true, total, items);
        } catch (Exception ex) {
            return new SearchResponse(false, 0, Collections.emptyList());
        }
    }

    private List<String> extractLemmas(String query) {
        return morphologyService.lemmatize(query).stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Long> buildDfMap(List<String> lemmas, String site) {
        Map<String, Long> dfMap = new HashMap<>();
        List<Object[]> dfRows;
        
        if (site == null || site.isBlank()) {
            dfRows = indexRepository.countDocsByLemma(lemmas);
        } else {
            dfRows = indexRepository.countDocsByLemmaAndSite(lemmas, site);
        }
        
        for (Object[] r : dfRows) {
            String lemma = (String) r[0];
            Number cnt = (Number) r[1];
            dfMap.put(lemma, cnt == null ? 0L : cnt.longValue());
        }
        
        return dfMap;
    }

    private long countTotalPages(String site) {
        if (site == null || site.isBlank()) {
            return indexRepository.countDistinctPages();
        } else {
            return indexRepository.countDistinctPagesBySite(site);
        }
    }

    private Map<Integer, Map<String, Double>> buildPageLemmaTfMap(List<String> lemmas, String site) {
        List<Object[]> tfRows;
        if (site == null || site.isBlank()) {
            tfRows = indexRepository.findPageLemmaTfByLemmas(lemmas);
        } else {
            tfRows = indexRepository.findPageLemmaTfByLemmasAndSite(lemmas, site);
        }

        Map<Integer, Map<String, Double>> pageLemmaTf = new HashMap<>();
        for (Object[] r : tfRows) {
            Integer pageId = (Integer) r[0];
            String lemma = (String) r[1];
            Number tfNum = (Number) r[2];
            double tf = tfNum == null ? 0.0 : tfNum.doubleValue();

            pageLemmaTf.computeIfAbsent(pageId, k -> new HashMap<>()).put(lemma, tf);
        }
        
        return pageLemmaTf;
    }

    private Map<String, Double> calculateIdfMap(List<String> lemmas, Map<String, Long> dfMap, long totalPages) {
        Map<String, Double> idfMap = new HashMap<>();
        for (String lemma : lemmas) {
            long df = dfMap.getOrDefault(lemma, 0L);
            double idf = Math.log((double)(totalPages + 1) / (double)(df + 1));
            idfMap.put(lemma, idf);
        }
        return idfMap;
    }

    private List<PageScore> calculatePageScores(Map<Integer, Map<String, Double>> pageLemmaTf, Map<String, Double> idfMap) {
        List<PageScore> pageScores = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Double>> entry : pageLemmaTf.entrySet()) {
            Integer pageId = entry.getKey();
            Map<String, Double> lemmaTfMap = entry.getValue();

            double score = calculateScore(lemmaTfMap, idfMap);
            pageScores.add(new PageScore(pageId, (float) score));
        }
        return pageScores;
    }

    private double calculateScore(Map<String, Double> lemmaTfMap, Map<String, Double> idfMap) {
        double score = 0.0;
        for (Map.Entry<String, Double> lt : lemmaTfMap.entrySet()) {
            String lemma = lt.getKey();
            double tf = lt.getValue();
            double idf = idfMap.getOrDefault(lemma, 0.0);
            score += tf * idf;
        }
        return score;
    }

    private List<PageScore> getPageScoresPage(List<PageScore> pageScores, int offset, int limit) {
        int from = Math.max(0, offset);
        int to = Math.min(pageScores.size(), offset + Math.max(1, limit));
        return pageScores.subList(from, to);
    }

    private List<SearchItem> buildSearchItems(List<PageScore> pageScoresPage, String query, List<String> lemmas) {
        List<Integer> ids = pageScoresPage.stream().map(ps -> ps.pageId).collect(Collectors.toList());
        List<PageEntity> pages = pageRepository.findAllWithSiteByIdIn(ids);
        Map<Integer, PageEntity> pageById = pages.stream().collect(Collectors.toMap(PageEntity::getId, p -> p));

        List<String> queryTokens = extractQueryTokens(query);
        List<SearchItem> items = new ArrayList<>();
        
        for (PageScore ps : pageScoresPage) {
            PageEntity page = pageById.get(ps.pageId);
            if (page == null) continue;

            SearchItem item = createSearchItem(page, ps, queryTokens, lemmas);
            items.add(item);
        }
        
        return items;
    }

    private List<String> extractQueryTokens(String query) {
        return Arrays.stream(query.split("\\s+"))
                .map(s -> s.replaceAll("[^\\p{L}\\p{Nd}]", ""))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private SearchItem createSearchItem(PageEntity page, PageScore ps, List<String> queryTokens, List<String> lemmas) {
        SearchItem item = new SearchItem();
        item.setSite(page.getSite().getUrl());
        item.setSiteName(page.getSite().getName());
        item.setUri(buildFullUrl(page));
        item.setTitle(extractTitle(page));

        List<String> snippetWords = !queryTokens.isEmpty() ? queryTokens : lemmas;
        item.setSnippet(snippetService.generateSnippet(page.getContent(), snippetWords));

        item.setRelevance(ps.score);
        return item;
    }

    private String buildFullUrl(PageEntity page) {
        String siteUrl = page.getSite().getUrl();
        String path = page.getPath();
        if (path == null) path = "";

        String trimmed = path.trim();

        // Если path уже содержит полный URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // Нормализуем siteUrl для сравнения (убираем завершающий слэш)
            String normalizedSiteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
            String normalizedPath = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            
            // Проверяем, не содержит ли path уже siteUrl (чтобы избежать дублирования)
            if (normalizedPath.startsWith(normalizedSiteUrl)) {
                return trimmed;
            }
            // Если это другой домен, возвращаем как есть
            return trimmed;
        }

        // Если path пустой, возвращаем siteUrl с завершающим слэшем
        if (trimmed.isEmpty()) {
            return siteUrl.endsWith("/") ? siteUrl : siteUrl + "/";
        }

        // Нормализуем слэши
        if (!siteUrl.endsWith("/") && !trimmed.startsWith("/")) {
            return siteUrl + "/" + trimmed;
        } else if (siteUrl.endsWith("/") && trimmed.startsWith("/")) {
            return siteUrl + trimmed.substring(1);
        } else {
            return siteUrl + trimmed;
        }
    }


    private String extractTitle(PageEntity page) {
        String content = page.getContent();
        if (content == null || content.isBlank()) return page.getPath();
        String trimmed = content.trim();
        int end = Math.min(trimmed.length(), 120);
        String candidate = trimmed.substring(0, end);
        if (candidate.length() == end && end < trimmed.length()) {
            int lastSpace = candidate.lastIndexOf(' ');
            if (lastSpace > 10) candidate = candidate.substring(0, lastSpace) + "...";
        }
        return candidate;
    }

    private static class PageScore {
        final Integer pageId;
        final float score;
        PageScore(Integer pageId, float score) { this.pageId = pageId; this.score = score; }
    }
}
