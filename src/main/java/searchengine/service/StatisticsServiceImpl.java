package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public StatisticsResponse getStatistics() {
        List<SiteEntity> sites = siteRepository.findAll();

        StatisticsResponse.Total total = new StatisticsResponse.Total();
        List<StatisticsResponse.Detailed> detailedList = new ArrayList<>();

        int sitesCount = sites.size();
        long pagesSum = 0L;
        long lemmasSum = 0L;

        for (SiteEntity site : sites) {
            // safe counts via repository methods
            long pages = pageRepository.countBySite(site);
            long lemmas = lemmaRepository.countBySite(site);

            pagesSum += pages;
            lemmasSum += lemmas;

            StatisticsResponse.Detailed d = new StatisticsResponse.Detailed();
            d.setUrl(site.getUrl());
            d.setName(site.getName());
            d.setStatus(site.getStatus() != null ? site.getStatus().name() : null);
            d.setStatusTime(site.getStatusTime() != null ? site.getStatusTime().toString() : null);
            d.setPages((int) pages);
            d.setLemmas((int) lemmas);

            detailedList.add(d);
        }

        total.setSites(sitesCount);
        total.setPages((int) pagesSum);
        total.setLemmas((int) lemmasSum);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setTotal(total);
        response.setDetailed(detailedList);

        return response;
    }
}
