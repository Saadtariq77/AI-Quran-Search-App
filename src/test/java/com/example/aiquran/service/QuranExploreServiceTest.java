package com.example.aiquran.service;

import com.example.aiquran.dto.AyatExploreRequest;
import com.example.aiquran.dto.AyatExploreResponse;
import com.example.aiquran.dto.TranslationRow;
import com.example.aiquran.exception.LlmUnavailableException;
import com.example.aiquran.repository.TranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuranExploreServiceTest {

    @Mock
    private LlmService llmService;

    @Mock
    private TranslationRepository translationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private QuranExploreService exploreService;

    @BeforeEach
    void setUp() {
        exploreService = new QuranExploreService(llmService, translationRepository, objectMapper);
    }

    @Test
    void explore_success() {
        String mockLlmJson = """
                {
                  "explanation": "This verse teaches patience and seeking help through prayer.",
                  "historicalContext": {
                    "event": "Foundational Guidance for the Early Muslim Community",
                    "tafsirSource": "Tafsir Ibn Kathir",
                    "details": "Revealed in Madinah to strengthen the believers facing trials."
                  },
                  "relatedHadiths": [
                    {
                      "narrator": "Narrated by Suhaib (RA)",
                      "hadithText": "Strange are the ways of a believer for there is good in every affair...",
                      "sourceBook": "Sahih Muslim 2999",
                      "connection": "Directly illustrates the virtue of patience (sabr) and gratitude."
                    }
                  ],
                  "authenticSources": [
                    "Tafsir Ibn Kathir",
                    "Sahih Muslim"
                  ]
                }
                """;

        when(llmService.generateJson(anyString(), anyString())).thenReturn(mockLlmJson);

        AyatExploreRequest request = AyatExploreRequest.builder()
                .surahNo(2)
                .ayatNo(153)
                .translation("O you who have believed, seek help through patience and prayer. Indeed, Allah is with the patient.")
                .build();

        AyatExploreResponse response = exploreService.explore(request);

        assertThat(response).isNotNull();
        assertThat(response.getSurahNo()).isEqualTo(2);
        assertThat(response.getAyatNo()).isEqualTo(153);
        assertThat(response.getReference()).isEqualTo("2:153");
        assertThat(response.getSurahNameEn()).isEqualTo("Al-Baqarah");
        assertThat(response.getSurahNameAr()).isEqualTo("البقرة");
        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getExplanation()).contains("patience and seeking help");
        assertThat(response.getHistoricalContext()).isNotNull();
        assertThat(response.getHistoricalContext().getTafsirSource()).isEqualTo("Tafsir Ibn Kathir");
        assertThat(response.getRelatedHadiths()).hasSize(1);
        assertThat(response.getRelatedHadiths().get(0).getSourceBook()).isEqualTo("Sahih Muslim 2999");
        assertThat(response.getAuthenticSources()).contains("Tafsir Ibn Kathir", "Sahih Muslim");
    }

    @Test
    void explore_llmUnavailable_fallsBackGracefully() {
        when(llmService.generateJson(anyString(), anyString()))
                .thenThrow(new LlmUnavailableException("Groq rate limit exceeded"));

        AyatExploreRequest request = AyatExploreRequest.builder()
                .surahNo(2)
                .ayatNo(153)
                .translation("O you who have believed, seek help through patience and prayer.")
                .build();

        AyatExploreResponse response = exploreService.explore(request);

        assertThat(response).isNotNull();
        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getExplanation()).contains("temporarily unavailable");
    }
}
