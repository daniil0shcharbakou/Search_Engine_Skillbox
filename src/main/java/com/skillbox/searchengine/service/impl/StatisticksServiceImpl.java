package com.skillbox.searchengine.service.impl;

import com.skillbox.searchengine.dto.statistics.DetailedStatistics;
import com.skillbox.searchengine.dto.statistics.StatisticsResponse;
import com.skillbox.searchengine.repository.IndexRepository;
import com.skillbox.searchengine.repository.LemmaRepository;
import com.skillbox.searchengine.repository.PageRepository;
import com.skillbox.searchengine.repository.SiteRepository;
import com.skillbox.searchengine.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;

    @Override
    public StatisticsResponse getStatistics() {
        long totalSites = siteRepo.count();
        long totalPages = pageRepo.count();
        long totalLemmas = lemmaRepo.count();
        List<DetailedStatistics> detailed = siteRepo.findAll().stream().map(site ->
            new DetailedStatistics(
                site.getName(),
                site.getUrl(),
                site.getStatus().name(),
                site.getStatusTime().toString(),
                pageRepo.findAll().stream().filter(p -> p.getSite().equals(site)).count(),
                lemmaRepo.findAll().stream().filter(l -> l.getSite().equals(site)).count()
            )
        ).collect(Collectors.toList());
        return new StatisticsResponse(totalSites, totalPages, totalLemmas, detailed);
    }
}
