package com.example.aiquran.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AyatExploreRequest {

    private Integer surahNo;

    private Integer ayatNo;

    private String translation;

    private String translationUrdu;
}
