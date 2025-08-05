package com.skillbox.searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DetailedStatistics {
    private String name;
    private String url;
    private String status;
    private String statusTime;
    private long pages;
    private long lemmas;
}
