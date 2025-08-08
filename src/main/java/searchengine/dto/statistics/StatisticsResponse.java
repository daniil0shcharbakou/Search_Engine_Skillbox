package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

@Data
public class StatisticsResponse {
    private boolean result;
    private Total total;
    private List<Detailed> detailed;

    @Data
    public static class Total {
        private int sites;
        private int pages;
        private int lemmas;
    }

    @Data
    public static class Detailed {
        private String url;
        private String name;
        private String status;
        private String statusTime;
        private int pages;
        private int lemmas;
    }
}
