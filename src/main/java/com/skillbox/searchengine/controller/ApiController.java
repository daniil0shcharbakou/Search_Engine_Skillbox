package com.skillbox.searchengine.controller;

import com.skillbox.searchengine.dto.search.SearchResponse;
import com.skillbox.searchengine.dto.statistics.StatisticsResponse;
import com.skillbox.searchengine.service.IndexingService;
import com.skillbox.searchengine.service.SearchService;
import com.skillbox.searchengine.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {
    private final IndexingService indexingService;
    private final SearchService searchService;
    private final StatisticsService statisticsService;

    @GetMapping("/startIndexing")
    public String start() {
        indexingService.startIndexing();
        return "Indexing started";
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        return searchService.search(query, offset, limit);
    }

    @GetMapping("/statistics")
    public StatisticsResponse stats() {
        return statisticsService.getStatistics();
    }
}
