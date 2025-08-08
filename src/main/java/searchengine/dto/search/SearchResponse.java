package searchengine.dto.search;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchItem> data;

    public SearchResponse(boolean result, int count, List<SearchItem> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}
