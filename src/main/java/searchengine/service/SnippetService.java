package searchengine.service;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SnippetService {

    // Максимальная длина куска вокруг найденного слова
    private static final int WINDOW = 60;
    // Максимальная итоговая длина сниппета
    private static final int MAX_SNIPPET_LENGTH = 300;

    public String generateSnippet(String content, List<String> queryWords) {
        if (content == null || content.isBlank()) return "";

        // Удаляем HTML и нормализуем пробельные символы
        String text = Jsoup.parse(content).text();
        text = text.replaceAll("\\s+", " ").trim();
        String lower = text.toLowerCase(Locale.ROOT);

        // Подготавливаем набор поисковых слов (без пустых)
        Set<String> words = queryWords == null ? Collections.emptySet()
                : queryWords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> segments = new ArrayList<>();

        // Ищем вхождения слов — берем до 2-х фрагментов
        for (String w : words) {
            int idx = lower.indexOf(w);
            if (idx >= 0) {
                segments.add(extractSegment(text, idx, w.length()));
                if (segments.size() >= 2) break;
            }
        }

        // Если ничего не найдено — вернём начало текста (fallback)
        if (segments.isEmpty()) {
            if (text.length() <= MAX_SNIPPET_LENGTH) return text;
            return text.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
        }

        String snippet = String.join(" ... ", segments);
        if (snippet.length() > MAX_SNIPPET_LENGTH) {
            snippet = snippet.substring(0, MAX_SNIPPET_LENGTH).trim();
            // не обрываем слово в середине (попробуем обрезать до последнего пробела)
            int lastSpace = snippet.lastIndexOf(' ');
            if (lastSpace > snippet.length() / 2) snippet = snippet.substring(0, lastSpace);
            snippet = snippet + "...";
        }
        return snippet;
    }

    private String extractSegment(String text, int matchIndex, int matchLen) {
        int start = Math.max(0, matchIndex - WINDOW);
        int end = Math.min(text.length(), matchIndex + matchLen + WINDOW);
        String seg = text.substring(start, end).trim();

        if (start > 0) seg = "..." + seg;
        if (end < text.length()) seg = seg + "...";
        return seg;
    }
}
