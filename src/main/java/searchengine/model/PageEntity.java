package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "page")
@Data
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    private SiteEntity site;

    private String path;
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;
}
