package com.skillbox.searchengine.util;

import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.apache.lucene.morphology.LuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmaUtils {
    private static final LuceneMorphology morphology;
    static {
        try {
            morphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Map<String, Integer> getLemmaCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        List<String> words = Arrays.asList(text.toLowerCase().split("\\W+"));
        for (String w : words) {
            List<String> base = morphology.getNormalForms(w);
            if (!base.isEmpty()) {
                String lemma = base.get(0);
                if (!morphology.getMorphInfo(lemma).contains("UNKN")) {
                    counts.put(lemma, counts.getOrDefault(lemma, 0) + 1);
                }
            }
        }
        return counts;
    }
}
