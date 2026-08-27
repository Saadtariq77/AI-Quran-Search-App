package com.example.aiquran.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AyatDto {

    private Long id;

    private Integer surahNo;

    private Integer ayatNo;

    private String translation;

    private String translationUrdu;

    private Double relevanceScore;

    private Integer keywordHits;

    private List<String> matchedConcepts;

    private String aiReason;

    private boolean aiSelected;

    public String getReference() {
        return surahNo + ":" + ayatNo;
    }
}
