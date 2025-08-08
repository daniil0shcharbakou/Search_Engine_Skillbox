package searchengine.service;

import searchengine.dto.search.SearchResponse;

public interface SearchService {
    SearchResponse search(String query, String site, int offset, int limit);
}
