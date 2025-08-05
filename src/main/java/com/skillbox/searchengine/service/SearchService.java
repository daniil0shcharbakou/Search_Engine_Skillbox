package com.skillbox.searchengine.service;

import com.skillbox.searchengine.dto.search.SearchResult;

import java.util.List;

public interface SearchService {
    List<SearchResult> search(String query, int offset, int limit);
}
