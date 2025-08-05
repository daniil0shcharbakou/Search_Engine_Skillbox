package com.skillbox.searchengine.repository;

import com.skillbox.searchengine.model.Page;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRepository extends JpaRepository<Page, Long> {}
