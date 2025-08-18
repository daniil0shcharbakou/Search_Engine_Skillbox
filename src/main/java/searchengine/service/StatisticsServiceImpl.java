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

/**
 * Сервис формирования статистики для API.
 * Использует безопасный findFirstByUrl(...) чтобы не падать при дубликатах в таблице site.
 */
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

        // Total
        StatisticsResponse.Total total = new StatisticsResponse.Total();
        int configuredSites = sitesList.getSites() != null ? sitesList.getSites().size() : 0;
        total.setSites(configuredSites);

        long pagesCountAll = pageRepository.count();
        long lemmasCountAll = lemmaRepository.count();
        total.setPages((int) pagesCountAll);
        total.setLemmas((int) lemmasCountAll);

        // флаг — идёт ли индексация
        boolean running = false;
        try {
            if (indexingService != null) {
                running = indexingService.isIndexing();
            }
        } catch (Exception ignored) { }
        total.setIndexing(running);

        response.setTotal(total);

        // Detailed per configured site
        List<StatisticsResponse.Detailed> detailedList = new ArrayList<>();
        if (sitesList.getSites() != null) {
            // используем var — не завязываемся на конкретный тип элемента списка
            for (var cfgSite : sitesList.getSites()) {
                StatisticsResponse.Detailed d = new StatisticsResponse.Detailed();

                // cfgSite должен иметь методы getUrl() и getName()
                // Если в твоём классе имя методов другое — скажи, я подскажу.
                String cfgUrl = null;
                String cfgName = null;
                try {
                    cfgUrl = (String) cfgSite.getClass().getMethod("getUrl").invoke(cfgSite);
                } catch (Exception ignored) { }
                try {
                    cfgName = (String) cfgSite.getClass().getMethod("getName").invoke(cfgSite);
                } catch (Exception ignored) { }

                // fallback: если рефлексия не сработала, попытаемся вытащить toString()
                if (cfgUrl == null) cfgUrl = cfgSite.toString();
                if (cfgName == null) cfgName = cfgUrl;

                d.setUrl(cfgUrl);
                d.setName(cfgName);

                // безопасно: берем первую запись с таким URL (если есть дубликаты, не падаем)
                Optional<SiteEntity> optSiteEntity = siteRepository.findFirstByUrl(cfgUrl);
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
                    // сайт ещё не в БД
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
