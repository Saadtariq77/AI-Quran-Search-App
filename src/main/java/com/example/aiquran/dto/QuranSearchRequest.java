package com.example.aiquran.dto;


import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuranSearchRequest {

    private String query;

    private Integer maxAyats;   // optional override
}
