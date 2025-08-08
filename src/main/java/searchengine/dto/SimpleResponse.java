package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimpleResponse {
    private boolean result;
    private String error;
}
