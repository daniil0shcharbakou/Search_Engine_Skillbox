package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

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

    @Query("SELECT i.page.id, i.lemma.lemma, SUM(i.rank) FROM IndexEntity i " +
            "WHERE i.lemma.lemma IN :lemmas " +
            "GROUP BY i.page.id, i.lemma.lemma")
    List<Object[]> findPageLemmaTfByLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT i.page.id, i.lemma.lemma, SUM(i.rank) FROM IndexEntity i " +
            "WHERE i.lemma.lemma IN :lemmas AND i.page.site.url = :siteUrl " +
            "GROUP BY i.page.id, i.lemma.lemma")
    List<Object[]> findPageLemmaTfByLemmasAndSite(@Param("lemmas") List<String> lemmas,
                                                  @Param("siteUrl") String siteUrl);

    @Query("SELECT i.lemma.lemma, COUNT(DISTINCT i.page.id) FROM IndexEntity i " +
            "WHERE i.lemma.lemma IN :lemmas GROUP BY i.lemma.lemma")
    List<Object[]> countDocsByLemma(@Param("lemmas") List<String> lemmas);

    @Query("SELECT i.lemma.lemma, COUNT(DISTINCT i.page.id) FROM IndexEntity i " +
            "WHERE i.lemma.lemma IN :lemmas AND i.page.site.url = :siteUrl GROUP BY i.lemma.lemma")
    List<Object[]> countDocsByLemmaAndSite(@Param("lemmas") List<String> lemmas,
                                           @Param("siteUrl") String siteUrl);

    @Query("SELECT COUNT(DISTINCT i.page.id) FROM IndexEntity i")
    long countDistinctPages();

    @Query("SELECT COUNT(DISTINCT i.page.id) FROM IndexEntity i WHERE i.page.site.url = :siteUrl")
    long countDistinctPagesBySite(@Param("siteUrl") String siteUrl);

    void deleteByPage(PageEntity page);
}
