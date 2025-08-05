package com.skillbox.searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchResult {
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
