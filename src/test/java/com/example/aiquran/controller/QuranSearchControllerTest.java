package com.example.aiquran.controller;

import com.example.aiquran.dto.AyatExploreRequest;
import com.example.aiquran.dto.AyatExploreResponse;
import com.example.aiquran.dto.HistoricalContextDto;
import com.example.aiquran.dto.HadithReferenceDto;
import com.example.aiquran.service.QuranExploreService;
import com.example.aiquran.service.QuranKeywordService;
import com.example.aiquran.service.QuranSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class QuranSearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QuranSearchService quranSearchService;

    @Mock
    private QuranKeywordService keywordService;

    @Mock
    private QuranExploreService exploreService;

    @InjectMocks
    private QuranSearchController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void explore_post_returnsValidJson() throws Exception {
        AyatExploreResponse mockResponse = AyatExploreResponse.builder()
                .surahNo(2)
                .ayatNo(153)
                .reference("2:153")
                .surahNameEn("Al-Baqarah")
                .surahNameAr("البقرة")
                .explanation("Test explanation of verse 2:153.")
                .historicalContext(HistoricalContextDto.builder()
                        .event("Guidance for Believers")
                        .tafsirSource("Tafsir Ibn Kathir")
                        .details("Detailed historical context.")
                        .build())
                .relatedHadiths(List.of(HadithReferenceDto.builder()
                        .narrator("Narrated by Abu Hurairah (RA)")
                        .hadithText("Patience is light...")
                        .sourceBook("Sahih Muslim 223")
                        .connection("Emphasizes prayer and patience.")
                        .build()))
                .authenticSources(List.of("Tafsir Ibn Kathir", "Sahih Muslim"))
                .build();

        when(exploreService.explore(any(AyatExploreRequest.class))).thenReturn(mockResponse);

        AyatExploreRequest request = AyatExploreRequest.builder()
                .surahNo(2)
                .ayatNo(153)
                .build();

        mockMvc.perform(post("/api/quran/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surahNo").value(2))
                .andExpect(jsonPath("$.ayatNo").value(153))
                .andExpect(jsonPath("$.reference").value("2:153"))
                .andExpect(jsonPath("$.surahNameEn").value("Al-Baqarah"))
                .andExpect(jsonPath("$.historicalContext.tafsirSource").value("Tafsir Ibn Kathir"))
                .andExpect(jsonPath("$.relatedHadiths[0].sourceBook").value("Sahih Muslim 223"))
                .andExpect(jsonPath("$.authenticSources[0]").value("Tafsir Ibn Kathir"));
    }

    @Test
    void explore_get_returnsValidJson() throws Exception {
        AyatExploreResponse mockResponse = AyatExploreResponse.builder()
                .surahNo(2)
                .ayatNo(153)
                .reference("2:153")
                .surahNameEn("Al-Baqarah")
                .explanation("Test explanation")
                .build();

        when(exploreService.explore(any(AyatExploreRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/quran/explore")
                        .param("surah", "2")
                        .param("ayat", "153"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("2:153"));
    }
}
