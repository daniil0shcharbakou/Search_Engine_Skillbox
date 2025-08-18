package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexingService indexingService;

    private final DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(false);

        StatisticsResponse.Total total = new StatisticsResponse.Total();
        int configuredSites = sitesList.getSites() != null ? sitesList.getSites().size() : 0;
        total.setSites(configuredSites);
        // безопасно приводим long -> int (для небольших тестовых данных)
        total.setPages((int) pageRepository.count());
        total.setLemmas((int) lemmaRepository.count());

        // флаг: идёт ли индексация
        boolean running = false;
        try {
            running = indexingService != null && indexingService.isIndexing();
        } catch (Exception ignored) { }
        // ВАЖНО: используем setIndexing, т.к. поле называется 'indexing'
        total.setIndexing(running);

        response.setTotal(total);

        List<StatisticsResponse.Detailed> detailedList = new ArrayList<>();
        if (sitesList.getSites() != null) {
            for (var cfgSite : sitesList.getSites()) {
                StatisticsResponse.Detailed d = new StatisticsResponse.Detailed();
                d.setUrl(cfgSite.getUrl());
                d.setName(cfgSite.getName());

                Optional<SiteEntity> optSiteEntity = siteRepository.findByUrl(cfgSite.getUrl());
                if (optSiteEntity.isPresent()) {
                    SiteEntity siteEntity = optSiteEntity.get();
                    d.setStatus(siteEntity.getStatus() != null ? siteEntity.getStatus().name() : "UNKNOWN");
                    d.setStatusTime(siteEntity.getStatusTime() != null ? siteEntity.getStatusTime().format(dtf) : null);
                    long pagesCount = pageRepository.countBySite(siteEntity);
                    long lemmasCount = lemmaRepository.countBySite(siteEntity);
                    d.setPages((int) pagesCount);
                    d.setLemmas((int) lemmasCount);
                    d.setError(siteEntity.getLastError());
                } else {
                    d.setStatus("NOT_INDEXED");
                    d.setStatusTime(null);
                    d.setPages(0);
                    d.setLemmas(0);
                    d.setError(null);
                }
                detailedList.add(d);
            }
        }

        response.setDetailed(detailedList);
        response.setResult(true);
        return response;
    }
}
