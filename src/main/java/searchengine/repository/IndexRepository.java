package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Query("SELECT i.page.id, SUM(i.rank) FROM IndexEntity i " +
            "WHERE i.lemma.lemma IN :lemmas " +
            "GROUP BY i.page.id " +
            "ORDER BY SUM(i.rank) DESC")
    List<Object[]> findPageIdsAndScoresByLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT i.page.id, SUM(i.rank) FROM IndexEntity i " +
            "WHERE i.lemma.lemma IN :lemmas AND i.page.site.url = :siteUrl " +
            "GROUP BY i.page.id " +
            "ORDER BY SUM(i.rank) DESC")
    List<Object[]> findPageIdsAndScoresByLemmasAndSite(@Param("lemmas") List<String> lemmas,
                                                       @Param("siteUrl") String siteUrl);
}
