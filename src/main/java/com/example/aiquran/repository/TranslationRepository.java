package com.example.aiquran.repository;

import com.example.aiquran.dto.AyatMatch;
import com.example.aiquran.dto.TranslationRow;
import com.example.aiquran.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {

    @Query(value = """
            SELECT t.id           AS id,
                   t.surah_no     AS surahNo,
                   t.ayat_no      AS ayatNo,
                   t.translation  AS translation,
                   MATCH(t.translation) AGAINST (:q IN BOOLEAN MODE) AS score
            FROM translations t
            WHERE t.contribution_id = :contributionId
              AND MATCH(t.translation) AGAINST (:q IN BOOLEAN MODE)
            ORDER BY score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AyatMatch> fullTextSearch(@Param("q") String booleanQuery,
                                   @Param("contributionId") Long contributionId,
                                   @Param("limit") int limit);

    @Query(value = """
            SELECT t.id           AS id,
                   t.surah_no     AS surahNo,
                   t.ayat_no      AS ayatNo,
                   t.translation  AS translation,
                   1.0            AS score
            FROM translations t
            WHERE t.contribution_id = :contributionId
              AND LOWER(t.translation) LIKE LOWER(CONCAT('%', :term, '%'))
            LIMIT :limit
            """, nativeQuery = true)
    List<AyatMatch> likeSearch(@Param("term") String term,
                               @Param("contributionId") Long contributionId,
                               @Param("limit") int limit);

    @Query(value = """
            SELECT t.surah_no    AS surahNo,
                   t.ayat_no     AS ayatNo,
                   t.translation AS translation
            FROM translations t
            WHERE t.contribution_id = :contributionId
              AND (t.surah_no * 1000 + t.ayat_no) IN (:keys)
            """, nativeQuery = true)
    List<TranslationRow> findByContributionAndKeys(@Param("contributionId") Long contributionId,
                                                   @Param("keys") List<Integer> keys);
}