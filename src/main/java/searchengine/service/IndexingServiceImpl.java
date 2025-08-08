package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;
    private volatile boolean running = false;

    @Override
    public void startIndexing() {
        // TODO: запустить многопоточную индексацию всех sitesList.getSites()
    }

    @Override
    public void stopIndexing() {
        running = false;
    }

    @Override
    public void indexPage(String url) {
        // TODO: проиндексировать одну страницу по URL
    }
}
