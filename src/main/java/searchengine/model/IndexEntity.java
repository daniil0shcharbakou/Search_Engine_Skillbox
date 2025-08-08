package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "index")
@Data
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    private PageEntity page;

    @ManyToOne(fetch = FetchType.LAZY)
    private LemmaEntity lemma;

    private float rank;
}
