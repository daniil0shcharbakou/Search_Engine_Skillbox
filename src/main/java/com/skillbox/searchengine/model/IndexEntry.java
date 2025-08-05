package com.skillbox.searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class IndexEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private Page page;
    @ManyToOne
    private Lemma lemma;
    private float rank;
}
