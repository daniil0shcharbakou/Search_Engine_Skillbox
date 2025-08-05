package com.skillbox.searchengine.repository;

import com.skillbox.searchengine.model.Lemma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Long> {
    Optional<Lemma> findByLemmaAndSiteId(String lemma, Long siteId);
}
