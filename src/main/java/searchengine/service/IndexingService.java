package searchengine.service;

public interface IndexingService {
    void startIndexing();
    void stopIndexing();
    void indexPage(String url);

    /**
     * Возвращает, запущена ли сейчас индексация.
     * Используется в StatisticsService для формирования total.isIndexing,
     * а также фронтендом (через statistics.total.isIndexing).
     */
    boolean isIndexing();
}
