package com.skillbox.searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class StatisticsResponse {
    private long totalSites;
    private long totalPages;
    private long totalLemmas;
    private List<DetailedStatistics> details;
}
