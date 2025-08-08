package searchengine.service;

public interface IndexingService {
    void startIndexing();
    void stopIndexing();
    void indexPage(String url);
}
