package searchengine.service;

import searchengine.dto.SimpleResponse;

public interface IndexingService {
    SimpleResponse startIndexing();
    SimpleResponse stopIndexing();
    SimpleResponse indexPage(String url);

    boolean isIndexing();
}
