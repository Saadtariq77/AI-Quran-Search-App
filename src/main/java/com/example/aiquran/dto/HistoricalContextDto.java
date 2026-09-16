package com.example.aiquran.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalContextDto {

    /** Title or summary of the occasion of revelation (Asbab al-Nuzul) or context. */
    private String event;

    /** Name of classical book (e.g., "Tafsir Ibn Kathir", "Tafsir al-Tabari"). */
    private String tafsirSource;

    /** In-depth historical circumstances and explanation from verified sources. */
    private String details;
}
