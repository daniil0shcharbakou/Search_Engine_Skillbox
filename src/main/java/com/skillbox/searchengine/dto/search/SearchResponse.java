package com.skillbox.searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchResponse {
    private int count;
    private List<SearchResult> results;
}
