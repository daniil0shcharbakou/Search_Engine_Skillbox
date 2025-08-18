package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsApiResponse {
    /**
     * Внешний флаг успеха/ошибки (фронтенд проверяет result)
     */
    private boolean result;

    /**
     * Поле "statistics" — внутри будет total и detailed (как ожидает scripts.js)
     */
    private StatisticsResponse statistics;
}
