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
        if (query == null || query.isBlank()) {
            return new SearchResponse(false, 0, Collections.emptyList());
        }

        // 1) Лемматизация запроса (используем леммы для сопоставления с индексом)
        List<String> lemmas = morphologyService.lemmatize(query).stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());

        if (lemmas.isEmpty()) {
            return new SearchResponse(true, 0, Collections.emptyList());
        }

        // 2) Подготовка DF (document frequency) и N (количество документов)
        Map<String, Long> dfMap = new HashMap<>();
        long N;
        if (site == null || site.isBlank()) {
            List<Object[]> dfRows = indexRepository.countDocsByLemma(lemmas);
            for (Object[] r : dfRows) {
                String lemma = (String) r[0];
                Number cnt = (Number) r[1];
                dfMap.put(lemma, cnt == null ? 0L : cnt.longValue());
            }
            N = indexRepository.countDistinctPages();
        } else {
            List<Object[]> dfRows = indexRepository.countDocsByLemmaAndSite(lemmas, site);
            for (Object[] r : dfRows) {
                String lemma = (String) r[0];
                Number cnt = (Number) r[1];
                dfMap.put(lemma, cnt == null ? 0L : cnt.longValue());
            }
            N = indexRepository.countDistinctPagesBySite(site);
        }
        if (N <= 0) {
            return new SearchResponse(true, 0, Collections.emptyList());
        }

        // 3) Получаем TF (term frequency) по страницам: (pageId, lemma, tf)
        List<Object[]> tfRows;
        if (site == null || site.isBlank()) {
            tfRows = indexRepository.findPageLemmaTfByLemmas(lemmas);
        } else {
            tfRows = indexRepository.findPageLemmaTfByLemmasAndSite(lemmas, site);
        }

        // 4) Собираем структуру: pageId -> (lemma -> tf)
        Map<Integer, Map<String, Double>> pageLemmaTf = new HashMap<>();
        for (Object[] r : tfRows) {
            Integer pageId = (Integer) r[0];
            String lemma = (String) r[1];
            Number tfNum = (Number) r[2];
            double tf = tfNum == null ? 0.0 : tfNum.doubleValue();

            pageLemmaTf.computeIfAbsent(pageId, k -> new HashMap<>()).put(lemma, tf);
        }

        if (pageLemmaTf.isEmpty()) {
            return new SearchResponse(true, 0, Collections.emptyList());
        }

        // 5) Вычисляем IDF для каждой lemma: idf = ln((N + 1) / (df + 1))
        Map<String, Double> idfMap = new HashMap<>();
        for (String lemma : lemmas) {
            long df = dfMap.getOrDefault(lemma, 0L);
            double idf = Math.log((double)(N + 1) / (double)(df + 1)); // natural log
            idfMap.put(lemma, idf);
        }

        // 6) Считаем score для каждой page: sum(tf * idf)
        List<PageScore> pageScores = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Double>> entry : pageLemmaTf.entrySet()) {
            Integer pageId = entry.getKey();
            Map<String, Double> lemmaTfMap = entry.getValue();

            double score = 0.0;
            for (Map.Entry<String, Double> lt : lemmaTfMap.entrySet()) {
                String lemma = lt.getKey();
                double tf = lt.getValue();
                double idf = idfMap.getOrDefault(lemma, 0.0);
                score += tf * idf;
            }

            // опционально: можно нормализовать score по длине документа (необязательно)
            pageScores.add(new PageScore(pageId, (float) score));
        }

        // 7) Сортируем по score desc
        pageScores.sort((a, b) -> Float.compare(b.score, a.score));

        int total = pageScores.size();
        if (total == 0) {
            return new SearchResponse(true, 0, Collections.emptyList());
        }

        // 8) Пагинация
        int from = Math.max(0, offset);
        int to = Math.min(pageScores.size(), offset + Math.max(1, limit));
        List<PageScore> pageScoresPage = pageScores.subList(from, to);

        // 9) Достаём страницы и собираем SearchItem в том же порядке
        List<Integer> ids = pageScoresPage.stream().map(ps -> ps.pageId).collect(Collectors.toList());
        List<PageEntity> pages = pageRepository.findAllWithSiteByIdIn(ids);
        Map<Integer, PageEntity> pageById = pages.stream().collect(Collectors.toMap(PageEntity::getId, p -> p));

        // подготовим токены оригинального запроса для сниппета (fallback на леммы)
        List<String> queryTokens = Arrays.stream(query.split("\\s+"))
                .map(s -> s.replaceAll("[^\\p{L}\\p{Nd}]", ""))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<SearchItem> items = new ArrayList<>();
        for (PageScore ps : pageScoresPage) {
            PageEntity page = pageById.get(ps.pageId);
            if (page == null) continue;

            SearchItem item = new SearchItem();
            item.setSite(page.getSite().getUrl());
            item.setSiteName(page.getSite().getName());
            item.setUri(buildFullUrl(page));
            item.setTitle(extractTitle(page));

            // сниппет: используем оригинальные токены если есть, иначе — леммы
            List<String> snippetWords = !queryTokens.isEmpty() ? queryTokens : lemmas;
            item.setSnippet(snippetService.generateSnippet(page.getContent(), snippetWords));

            item.setRelevance(ps.score);
            items.add(item);
        }

        return new SearchResponse(true, total, items);
    }

    private String buildFullUrl(PageEntity page) {
        String siteUrl = page.getSite().getUrl();
        String path = page.getPath();
        if (path == null) path = "";
        if (!siteUrl.endsWith("/") && !path.startsWith("/")) return siteUrl + "/" + path;
        return siteUrl + path;
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
