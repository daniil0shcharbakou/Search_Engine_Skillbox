package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    Optional<SiteEntity> findByUrl(String url);

    Optional<SiteEntity> findFirstByUrl(String url);

    List<SiteEntity> findAllByUrl(String url);
}
