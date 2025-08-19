package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    Optional<SiteEntity> findByUrl(String url);

    /**
     * Возвращает первый попавшийся сайт с таким url (без падений при нескольких совпадениях).
     */
    Optional<SiteEntity> findFirstByUrl(String url);

    /**
     * При необходимости — получить все записи с одним url.
     */
    List<SiteEntity> findAllByUrl(String url);
}
