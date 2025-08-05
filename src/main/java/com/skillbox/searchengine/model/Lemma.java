package com.skillbox.searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String lemma;
    private int frequency;
    @ManyToOne
    private Site site;
}
