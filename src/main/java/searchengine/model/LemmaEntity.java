package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "lemma")
@Data
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    private SiteEntity site;

    private String lemma;
    private int frequency;
}
