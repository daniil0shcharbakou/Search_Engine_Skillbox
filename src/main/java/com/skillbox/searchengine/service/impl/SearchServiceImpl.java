package com.skillbox.searchengine.service.impl;

import com.skillbox.searchengine.dto.search.SearchResult;
import com.skillbox.searchengine.model.IndexEntry;
import com.skillbox.searchengine.repository.IndexRepository;
import com.skillbox.searchengine.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final IndexRepository indexRepo;

    @Override
    public List<SearchResult> search(String query, int offset, int limit) {
        var lemma = query.toLowerCase();
        List<IndexEntry> entries = indexRepo.findAll().stream()
                .filter(e -> e.getLemma().getLemma().equals(lemma))
                .sorted((a, b) -> Float.compare(b.getRank(), a.getRank()))
                .collect(Collectors.toList());
        return entries.stream()
                .skip(offset)
                .limit(limit)
                .map(e -> new SearchResult(
                        e.getPage().getPath(),
                        e.getPage().getPath(),
                        e.getPage().getContent().substring(0, Math.min(200, e.getPage().getContent().length())),
                        e.getRank()))
                .collect(Collectors.toList());
    }
}
