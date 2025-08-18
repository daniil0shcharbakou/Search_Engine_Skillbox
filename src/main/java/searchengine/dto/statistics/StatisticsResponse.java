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
        /**
         * Фронтенд ожидает флаг о том, идёт ли индексирование.
         * Название поля — "indexing" чтобы Lombok корректно сгенерировал setIndexing(...)
         */
        private boolean indexing;
    }

    @Data
    public static class Detailed {
        private String url;
        private String name;
        private String status;
        private String statusTime;
        private int pages;
        private int lemmas;
        /**
         * Сообщение об ошибке (если есть)
         */
        private String error;
    }
}
