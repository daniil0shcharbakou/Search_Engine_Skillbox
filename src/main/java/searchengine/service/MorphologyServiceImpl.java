package searchengine.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MorphologyServiceImpl implements MorphologyService {

    @Override
    public List<String> lemmatize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase().split("\\P{L}+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
