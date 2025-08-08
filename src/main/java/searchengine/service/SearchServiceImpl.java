package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        // TODO: реализовать поиск: найти леммы, вычислить релевантность, собрать сниппеты
        return new SearchResponse(true, 0, List.of());
    }
}
