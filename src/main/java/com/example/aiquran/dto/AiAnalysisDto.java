package com.example.aiquran.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnalysisDto {

    private List<Selection> selected;
    private String reflection;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Selection {
        private Integer surah;      // wrapper types — Jackson 3 fails on null primitives
        private Integer ayat;
        private String reason;
    }
}