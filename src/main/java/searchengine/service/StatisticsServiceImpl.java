package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.statistics.StatisticsApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    @Transactional(readOnly = true)
    public StatisticsApiResponse getStatistics() {
        List<SiteEntity> sites = siteRepository.findAll();
        StatisticsResponse.Total total = buildTotal(sites);
        List<StatisticsResponse.Detailed> detailedList = buildDetailedList(sites);
        
        StatisticsResponse statistics = new StatisticsResponse();
        statistics.setResult(true);
        statistics.setTotal(total);
        statistics.setDetailed(detailedList);

        StatisticsApiResponse apiResponse = new StatisticsApiResponse();
        apiResponse.setResult(true);
        apiResponse.setStatistics(statistics);
        
        return apiResponse;
    }

    private StatisticsResponse.Total buildTotal(List<SiteEntity> sites) {
        StatisticsResponse.Total total = new StatisticsResponse.Total();
        int sitesCount = sites.size();
        long pagesSum = 0L;
        long lemmasSum = 0L;

        for (SiteEntity site : sites) {
            pagesSum += pageRepository.countBySite(site);
            lemmasSum += lemmaRepository.countBySite(site);
        }

        total.setSites(sitesCount);
        total.setPages((int) pagesSum);
        total.setLemmas((int) lemmasSum);
        
        return total;
    }

    private List<StatisticsResponse.Detailed> buildDetailedList(List<SiteEntity> sites) {
        List<StatisticsResponse.Detailed> detailedList = new ArrayList<>();
        
        for (SiteEntity site : sites) {
            StatisticsResponse.Detailed d = buildDetailed(site);
            detailedList.add(d);
        }
        
        return detailedList;
    }

    private StatisticsResponse.Detailed buildDetailed(SiteEntity site) {
        long pages = pageRepository.countBySite(site);
        long lemmas = lemmaRepository.countBySite(site);

        StatisticsResponse.Detailed d = new StatisticsResponse.Detailed();
        d.setUrl(site.getUrl());
        d.setName(site.getName());
        d.setStatus(site.getStatus() != null ? site.getStatus().name() : null);
        d.setStatusTime(site.getStatusTime() != null ? site.getStatusTime().toString() : null);
        d.setPages((int) pages);
        d.setLemmas((int) lemmas);
        d.setError(site.getLastError());
        
        return d;
    }
}
