package com.example.aiquran.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuranSearchResponse {

    private String userQuery;
    private List<String> themes;
    private List<String> keywordsUsed;

    /** AI-selected verses first (in AI order), then remaining matches by score. */
    private List<AyatDto> ayats;

    /** Short synthesis across the selected verses. Null when AI unavailable. */
    private String reflection;

    private boolean degraded;
    private long tookMs;
}