package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.service.IndexingService;
import searchengine.service.SearchService;
import searchengine.service.StatisticsService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics() {
        StatisticsResponse resp = statisticsService.getStatistics();

        Map<String, Object> body = new HashMap<>();
        boolean resultFlag = false;

        if (resp != null) {
            try {
                Method m = resp.getClass().getMethod("getResult");
                Object v = m.invoke(resp);
                if (v instanceof Boolean) resultFlag = (Boolean) v;
            } catch (NoSuchMethodException ignored) {
                try {
                    Method m2 = resp.getClass().getMethod("isResult");
                    Object v2 = m2.invoke(resp);
                    if (v2 instanceof Boolean) resultFlag = (Boolean) v2;
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored2) {
                    // методов нет или вызов не удался — оставляем false
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // если вызов не удался — оставляем false
            }
        }

        body.put("result", resultFlag);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total", resp != null ? resp.getTotal() : null);
        statistics.put("detailed", resp != null ? resp.getDetailed() : null);

        body.put("statistics", statistics);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/startIndexing")
    public Map<String, Object> startIndexing() {
        Map<String, Object> resp = new HashMap<>();
        try {
            indexingService.startIndexing();
            resp.put("result", true);
            resp.put("error", null);
        } catch (RuntimeException ex) {
            resp.put("result", false);
            resp.put("error", ex.getMessage());
        } catch (Exception ex) {
            resp.put("result", false);
            resp.put("error", "Internal error: " + ex.getMessage());
        }
        return resp;
    }

    @GetMapping("/stopIndexing")
    public Map<String, Object> stopIndexing() {
        Map<String, Object> resp = new HashMap<>();
        try {
            indexingService.stopIndexing();
            resp.put("result", true);
            resp.put("error", null);
        } catch (Exception ex) {
            resp.put("result", false);
            resp.put("error", "Internal error: " + ex.getMessage());
        }
        return resp;
    }

    @PostMapping("/indexPage")
    public Map<String, Object> indexPage(@RequestParam("url") String url) {
        Map<String, Object> resp = new HashMap<>();
        try {
            indexingService.indexPage(url);
            resp.put("result", true);
            resp.put("error", null);
        } catch (RuntimeException ex) {
            resp.put("result", false);
            resp.put("error", ex.getMessage());
        } catch (Exception ex) {
            resp.put("result", false);
            resp.put("error", "Internal error: " + ex.getMessage());
        }
        return resp;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String site,
            @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        try {
            return searchService.search(query, site, offset, limit);
        } catch (Exception ex) {
            return new SearchResponse(false, 0, java.util.Collections.emptyList());
        }
    }
}
