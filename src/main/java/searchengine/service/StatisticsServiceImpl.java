package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.StatisticsResponse;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    // Код из заготовки; метод getStatistics() возвращает актуальную статистику

    @Override
    public StatisticsResponse getStatistics() {
        // TODO: собрать данные по site, page, lemma
        return new StatisticsResponse();
    }
}
