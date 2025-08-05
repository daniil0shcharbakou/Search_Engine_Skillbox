package com.skillbox.searchengine.repository;

import com.skillbox.searchengine.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {}
