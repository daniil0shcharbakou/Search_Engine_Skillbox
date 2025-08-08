package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@Data
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime statusTime;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    private String url;
    private String name;

    public enum Status {
        INDEXING, INDEXED, FAILED
    }
}
