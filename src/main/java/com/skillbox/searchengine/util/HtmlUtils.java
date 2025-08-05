package com.skillbox.searchengine.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HtmlUtils {
    public static String cleanText(String html) {
        Document doc = Jsoup.parse(html);
        return doc.text();
    }
}
