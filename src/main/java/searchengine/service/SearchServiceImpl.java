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

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final MorphologyService morphologyService;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;

    private static final int SNIPPET_RADIUS = 80;

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String site, int offset, int limit) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(false, 0, Collections.emptyList());
        }

        List<String> lemmas = morphologyService.lemmatize(query).stream()
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());

        if (lemmas.isEmpty()) {
            return new SearchResponse(true, 0, Collections.emptyList());
        }

        List<Object[]> rows;
        if (site == null || site.isBlank()) {
            rows = indexRepository.findPageIdsAndScoresByLemmas(lemmas);
        } else {
            rows = indexRepository.findPageIdsAndScoresByLemmasAndSite(lemmas, site);
        }

        List<PageScore> pageScores = new ArrayList<>();
        for (Object[] row : rows) {
            Integer pageId = (Integer) row[0];
            Double scoreD = (Double) row[1];
            float score = scoreD != null ? scoreD.floatValue() : 0f;
            pageScores.add(new PageScore(pageId, score));
        }

        int total = pageScores.size();

        if (total == 0) {
            return new SearchResponse(true, 0, Collections.emptyList());
        }

        int from = Math.max(0, offset);
        int to = Math.min(pageScores.size(), offset + Math.max(1, limit));
        List<PageScore> pageScoresPage = pageScores.subList(from, to);

        List<Integer> ids = pageScoresPage.stream().map(ps -> ps.pageId).collect(Collectors.toList());
        List<PageEntity> pages = pageRepository.findAllWithSiteByIdIn(ids);
        Map<Integer, PageEntity> pageById = pages.stream().collect(Collectors.toMap(PageEntity::getId, p -> p));

        List<SearchItem> items = new ArrayList<>();
        for (PageScore ps : pageScoresPage) {
            PageEntity page = pageById.get(ps.pageId);
            if (page == null) continue;

            SearchItem item = new SearchItem();
            item.setSite(page.getSite().getUrl());
            item.setSiteName(page.getSite().getName());
            item.setUri(buildFullUrl(page));
            item.setTitle(extractTitle(page));
            item.setSnippet(buildSnippet(page.getContent(), lemmas));
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

    private String buildSnippet(String content, List<String> lemmas) {
        if (content == null || content.isBlank()) return "";
        String lc = content.toLowerCase();

        int idx = -1;
        for (String lemma : lemmas) {
            int pos = lc.indexOf(lemma.toLowerCase());
            if (pos >= 0 && (idx == -1 || pos < idx)) idx = pos;
        }
        if (idx == -1) return ellipsize(content.trim(), SNIPPET_RADIUS * 2);

        int start = Math.max(0, idx - SNIPPET_RADIUS);
        int end = Math.min(content.length(), idx + SNIPPET_RADIUS);
        String snippet = content.substring(start, end).trim();
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        return snippet;
    }

    private String ellipsize(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3).trim() + "...";
    }

    private static class PageScore {
        final Integer pageId;
        final float score;
        PageScore(Integer pageId, float score) { this.pageId = pageId; this.score = score; }
    }
}
