package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.SimpleResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.service.IndexingService;
import searchengine.service.SearchService;
import searchengine.service.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final IndexingService indexingService;
    private final StatisticsService statisticsService;
    private final SearchService searchService;

    @GetMapping("/startIndexing")
    public ResponseEntity<SimpleResponse> startIndexing() {
        // TODO: вызвать indexingService.startIndexing()
        return ResponseEntity.ok(new SimpleResponse(true, null));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<SimpleResponse> stopIndexing() {
        indexingService.stopIndexing();
        return ResponseEntity.ok(new SimpleResponse(true, null));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<SimpleResponse> indexPage(@RequestParam String url) {
        indexingService.indexPage(url);
        return ResponseEntity.ok(new SimpleResponse(true, null));
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(searchService.search(query, site, offset, limit));
    }
}
