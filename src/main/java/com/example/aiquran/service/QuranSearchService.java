package com.example.aiquran.service;

import com.example.aiquran.dto.*;
import com.example.aiquran.exception.GeminiUnavailableException;
import com.example.aiquran.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuranSearchService {

    private final QuranKeywordService keywordService;
    private final AyatSearchService ayatSearchService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @Value("${quran.search.max-ayats:6}")
    private int defaultMaxAyats;

    private static final String SYSTEM_PROMPT = """
            You are a respectful Quran study assistant.
            You receive a user's question and a numbered list of candidate ayats
            (Surah:Ayat + English translation) retrieved from a verified database.

            Return STRICT JSON only. No markdown, no prose outside the JSON.

            SCHEMA
            {
              "selected": [
                {"surah": 2, "ayat": 153, "reason": "one sentence, max 25 words"}
              ],
              "reflection": "2 to 3 sentences connecting the selected verses to the question"
            }

            HARD RULES
            1. Choose ONLY from the candidates listed. NEVER invent surah or ayat numbers.
            2. Select 3 to 6 candidates, most relevant first.
            3. Discard candidates that are not genuinely relevant, and any that state the
               OPPOSITE of what the user is seeking.
            4. In "reason", do NOT quote or restate the translation. The user already sees
               the verse text. Explain only WHY it answers their question.
            5. If nothing is relevant, return "selected": [] and say so in "reflection".
            6. No religious rulings or fatwa. Offer reflection, not verdicts.
            """;

    public QuranSearchResponse search(QuranSearchRequest request) {
        long start = System.currentTimeMillis();
        String query = request.getQuery().trim().replaceAll("\\s+", " ");
        int limit = request.getMaxAyats() != null ? request.getMaxAyats() : defaultMaxAyats;

        KeywordExpansionDto expansion = keywordService.expand(query);
        List<AyatDto> ayats = ayatSearchService.search(expansion, limit);

        String reflection = null;
        boolean degraded = expansion.isDegraded();

        if (!ayats.isEmpty()) {
            try {
                AiAnalysisDto analysis = analyze(query, ayats);
                ayats = merge(ayats, analysis);
                reflection = analysis.getReflection();
            } catch (GeminiUnavailableException e) {
                log.warn("AI analysis unavailable for '{}': {}", query, e.getMessage());
                degraded = true;
            } catch (Exception e) {
                log.error("AI analysis failed for '{}'", query, e);
                degraded = true;
            }
        }

        return QuranSearchResponse.builder()
                .userQuery(query)
                .themes(expansion.getThemes())
                .keywordsUsed(expansion.allTerms())
                .ayats(ayats)
                .reflection(reflection)
                .degraded(degraded)
                .tookMs(System.currentTimeMillis() - start)
                .build();
    }

    private AiAnalysisDto analyze(String userQuery, List<AyatDto> ayats) {
        StringBuilder sb = new StringBuilder();
        sb.append("User question: \"").append(userQuery).append("\"\n\n");
        sb.append("Candidate ayats:\n\n");

        int i = 1;
        for (AyatDto a : ayats) {
            sb.append(i++).append(". [").append(a.getSurahNo()).append(":").append(a.getAyatNo())
                    .append("] ").append(a.getTranslation()).append("\n\n");
        }
        sb.append("Return the JSON object.");

        String raw = geminiService.generateJson(SYSTEM_PROMPT, sb.toString());
        log.debug("AI analysis raw: {}", raw);

        AiAnalysisDto dto = objectMapper.readValue(
                JsonUtils.extractJsonObject(raw), AiAnalysisDto.class);

        if (dto.getSelected() == null) {
            dto.setSelected(List.of());
        }
        return dto;
    }

    private List<AyatDto> merge(List<AyatDto> ayats, AiAnalysisDto analysis) {
        Map<String, AyatDto> byRef = new LinkedHashMap<>();
        for (AyatDto a : ayats) {
            byRef.put(a.getReference(), a);
        }

        List<AyatDto> ordered = new ArrayList<>();

        for (AiAnalysisDto.Selection s : analysis.getSelected()) {
            if (s.getSurah() == null || s.getAyat() == null) continue;

            String ref = s.getSurah() + ":" + s.getAyat();
            AyatDto match = byRef.remove(ref);

            if (match == null) {
                log.warn("AI selected {} which was not in the retrieved set — discarded", ref);
                continue;
            }
            match.setAiReason(s.getReason());
            match.setAiSelected(true);
            ordered.add(match);
        }

        ordered.addAll(byRef.values());

        log.debug("Merged: {} AI-selected, {} other", ordered.size() - byRef.size(), byRef.size());
        return ordered;
    }
}