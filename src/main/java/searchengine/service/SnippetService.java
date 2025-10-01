package searchengine.service;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class SnippetService {

    private static final int WINDOW = 60;
    private static final int MAX_SNIPPET_LENGTH = 300;

    public String generateSnippet(String content, List<String> queryWords) {
        if (content == null || content.isBlank()) return "";

        String text = Jsoup.parse(content).text();
        text = text.replaceAll("\\s+", " ").trim();
        String lower = text.toLowerCase(Locale.ROOT);

        Set<String> words = queryWords == null ? Collections.emptySet()
                : queryWords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> segments = new ArrayList<>();

        for (String w : words) {
            if (w.isBlank()) continue;
            int idx = lower.indexOf(w);
            if (idx >= 0) {
                String seg = extractSegment(text, idx, w.length());
                seg = highlightSegment(seg, w);
                segments.add(seg);
                if (segments.size() >= 2) break;
            }
        }

        if (segments.isEmpty()) {
            String fallback = text.length() <= MAX_SNIPPET_LENGTH ? text
                    : text.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
            return fallback;
        }

        String snippet = String.join(" ... ", segments);
        if (snippet.length() > MAX_SNIPPET_LENGTH) {
            snippet = snippet.substring(0, MAX_SNIPPET_LENGTH).trim();
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

    private String highlightSegment(String seg, String wordLower) {
        if (seg == null || seg.isEmpty() || wordLower == null || wordLower.isBlank()) return seg;

        String patternStr = "\\b" + Pattern.quote(wordLower) + "\\b";
        Pattern p = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m = p.matcher(seg);

        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String match = m.group();
            String replacement = "<b>" + Matcher.quoteReplacement(match) + "</b>";
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
