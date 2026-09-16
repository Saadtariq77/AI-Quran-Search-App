package com.example.aiquran.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HadithReferenceDto {

    /** Primary narrator or Sahabi (e.g. "Narrated by Ibn Abbas (RA)"). */
    private String narrator;

    /** English text or core narration of the Hadith explaining the verse. */
    private String hadithText;

    /** Authentic collection reference (e.g., "Sahih al-Bukhari, Book of Tafsir"). */
    private String sourceBook;

    /** Clear explanation of how this Hadith connects to and clarifies the Ayat. */
    private String connection;
}
