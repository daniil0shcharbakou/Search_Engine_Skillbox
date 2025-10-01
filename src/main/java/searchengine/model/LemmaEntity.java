package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "lemma",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "lemma"}))
@Data
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    private String lemma;
    private int frequency;
}
