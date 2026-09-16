package com.example.aiquran.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AyatExploreResponse {

    private Integer surahNo;

    private Integer ayatNo;

    private String reference;

    private String surahNameEn;

    private String surahNameAr;

    private String translation;

    private String translationUrdu;

    /** In-depth spiritual and thematic AI explanation with practical life lessons. */
    private String explanation;

    /** Authentic Asbab al-Nuzul or historical background. */
    private HistoricalContextDto historicalContext;

    /** Authentic related Hadiths with narrator and source citations. */
    private List<HadithReferenceDto> relatedHadiths;

    /** List of verified classical books cited (e.g. Tafsir Ibn Kathir, Sahih al-Bukhari). */
    private List<String> authenticSources;

    /** Indicates whether LLM failed and degraded fallback was used. */
    private boolean degraded;

    /** Processing time in milliseconds. */
    private long tookMs;
}
