package searchengine.service;

import java.util.List;

public interface MorphologyService {
    List<String> lemmatize(String text);
}
