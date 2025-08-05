package com.skillbox.searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String path;
    private int code;
    @Lob
    private String content;
    @ManyToOne
    private Site site;
}
