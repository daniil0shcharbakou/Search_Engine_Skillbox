package com.skillbox.searchengine.model;

import com.skillbox.searchengine.model.enums.SiteStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String url;
    private SiteStatus status;
    private LocalDateTime statusTime;
    private String lastError;
}
