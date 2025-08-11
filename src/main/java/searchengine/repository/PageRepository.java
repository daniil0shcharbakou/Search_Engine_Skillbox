package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    boolean existsBySiteAndPath(SiteEntity site, String path);

    long countBySite(SiteEntity site);

    @Query("select p from PageEntity p join fetch p.site where p.id in :ids")
    List<PageEntity> findAllWithSiteByIdIn(@Param("ids") List<Integer> ids);
}
