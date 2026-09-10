package com.example.aiquran.service;

import com.example.aiquran.dto.KeywordExpansionDto;
import com.example.aiquran.dto.KeywordExpansionDto.ConceptGroup;
import com.example.aiquran.exception.LlmUnavailableException;
import com.example.aiquran.util.QuranTextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuranKeywordService {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private static final Pattern FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    private static final String SYSTEM_PROMPT = """
            You expand search queries for a database of ENGLISH Quran translations.

            Your job: turn the user's words into CONCEPT GROUPS whose terms are likely to
            appear LITERALLY in a classical English translation of the Quran.

            CRITICAL RULES
            1. Output STRICT JSON only. No prose, no markdown fences.
            2. IGNORE intent words: want, need, wish, looking for, tell me, show me, help,
               please, verse, ayat, surah, quran. Extract only the SUBSTANTIVE concept.
            3. Each concept group MUST include every common inflection and part of speech:
               noun, plural, verb base form, third person, past tense, gerund, adjective, adverb.
               Example concept "marriage" -> terms: marriage, marriages, marry, marries,
               married, marrying, wed, wedded, wedlock, spouse, spouses, wife, wives,
               husband, husbands, betrothed.
               Example concept "patience" -> terms: patience, patient, patiently, persevere,
               perseverance, endure, endurance, steadfast, steadfastness, persevering, constancy.
            4. Use CLASSICAL TRANSLATION VOCABULARY, not modern casual English:
               steadfast, affliction, provision, recompense, wrongdoers, bestow, admonition,
               tribulation, righteous, transgress, sustenance, forbearance, grievous.
            5. importance = "core" for the query's main subject (1 to 3 groups).
               importance = "related" for supporting or adjacent ideas (2 to 4 groups).
            6. Do NOT include terms that appear in almost every verse
               (allah, god, lord, indeed, people, day, believe) unless that IS the query.
            7. phrases: 0 to 4 multi-word strings likely to appear verbatim.
            8. NEVER output verse numbers. NEVER quote the Quran.

            SCHEMA
            {
              "detectedLanguage": "en",
              "themes": ["..."],
              "concepts": [
                {"concept":"...","importance":"core","terms":["...","..."]},
                {"concept":"...","importance":"related","terms":["...","..."]}
              ],
              "phrases": ["..."]
            }
            """;

    @Cacheable(
            value  = "quranKeywords",
            key    = "#userQuery.toLowerCase().trim()",
            unless = "#result == null || #result.degraded"
    )
    public KeywordExpansionDto expand(String userQuery) {

        String userPrompt = """
                User query: "%s"

                Extract the substantive concepts and return the JSON object.
                """.formatted(userQuery.trim());

        String raw = null;
        try {
            raw = llmService.generateJson(SYSTEM_PROMPT, userPrompt);
            log.debug("LLM expansion raw: {}", raw);

            KeywordExpansionDto dto = objectMapper.readValue(extractJson(raw), KeywordExpansionDto.class);
            KeywordExpansionDto clean = sanitize(dto, userQuery);

            if (clean.getConcepts().isEmpty()) {
                log.warn("No usable concepts for '{}'. Raw: {}", userQuery, raw);
                return fallback(userQuery);
            }

            log.info("Expanded '{}' -> themes={} concepts={}",
                    userQuery, clean.getThemes(),
                    clean.getConcepts().stream()
                            .map(c -> c.getConcept() + "(" + c.getImportance() + "):" + c.getTerms().size())
                            .toList());
            return clean;

        } catch (LlmUnavailableException e) {
            log.warn("LLM unavailable during expansion of '{}': {}", userQuery, e.getMessage());
            return fallback(userQuery);
        } catch (Exception e) {
            log.error("Expansion failed for '{}'. Raw was: {}", userQuery, raw, e);
            return fallback(userQuery);
        }
    }

    private KeywordExpansionDto fallback(String userQuery) {
        List<String> tokens = new ArrayList<>(QuranTextUtils.tokenize(userQuery));
        log.warn("FALLBACK expansion for '{}' -> {} (not cached)", userQuery, tokens);

        return KeywordExpansionDto.builder()
                .detectedLanguage("en")
                .themes(List.of())
                .degraded(true)
                .concepts(tokens.isEmpty() ? List.of() : List.of(
                        ConceptGroup.builder()
                                .concept("fallback")
                                .importance("core")
                                .terms(tokens)
                                .build()))
                .phrases(List.of())
                .build();
    }
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        String trimmed = raw.trim();

        Matcher fence = FENCE.matcher(trimmed);
        if (fence.find()) return fence.group(1).trim();

        Matcher obj = JSON_OBJECT.matcher(trimmed);
        return obj.find() ? obj.group() : trimmed;
    }

    private KeywordExpansionDto sanitize(KeywordExpansionDto dto, String userQuery) {
        List<ConceptGroup> concepts = new ArrayList<>();
        Set<String> seenTerms = new HashSet<>();

        for (ConceptGroup g : nullSafe(dto.getConcepts())) {
            if (g.getTerms() == null) continue;

            List<String> terms = g.getTerms().stream()
                    .map(t -> t.toLowerCase().trim())
                    .filter(QuranTextUtils::isUseful)
                    .filter(t -> t.matches("[a-z ']+"))
                    .filter(seenTerms::add)              // a term belongs to one group only
                    .toList();

            if (terms.isEmpty()) continue;

            concepts.add(ConceptGroup.builder()
                    .concept(g.getConcept() != null ? g.getConcept().toLowerCase().trim() : terms.get(0))
                    .importance(g.isCore() ? "core" : "related")
                    .terms(terms)
                    .build());
        }

        List<String> missed = QuranTextUtils.tokenize(userQuery).stream()
                .filter(t -> !seenTerms.contains(t))
                .filter(t -> concepts.stream().noneMatch(c ->
                        c.getTerms().stream().anyMatch(term ->
                                QuranTextUtils.stem(term).equals(QuranTextUtils.stem(t)))))
                .toList();

        if (!missed.isEmpty()) {
            log.debug("Adding user tokens LLM missed: {}", missed);
            concepts.add(ConceptGroup.builder()
                    .concept("user-terms")
                    .importance("related")
                    .terms(missed)
                    .build());
        }

        return KeywordExpansionDto.builder()
                .detectedLanguage(dto.getDetectedLanguage() != null ? dto.getDetectedLanguage() : "en")
                .themes(nullSafe(dto.getThemes()))
                .degraded(false)
                .concepts(concepts)
                .phrases(nullSafe(dto.getPhrases()).stream()
                        .map(p -> p.toLowerCase().trim())
                        .filter(p -> p.contains(" ") && p.length() >= 6)
                        .distinct()
                        .toList())
                .build();
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}