package com.skillbox.searchengine.repository;

import com.skillbox.searchengine.model.IndexEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexRepository extends JpaRepository<IndexEntry, Long> {}
