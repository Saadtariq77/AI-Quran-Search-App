package com.example.aiquran.service;

import com.example.aiquran.dto.AyatDto;
import com.example.aiquran.dto.AyatMatch;
import com.example.aiquran.dto.KeywordExpansionDto;
import com.example.aiquran.dto.TranslationRow;
import com.example.aiquran.repository.TranslationRepository;
import com.example.aiquran.util.QuranTextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class    AyatSearchService {

    @Value("${quran.translation.english-contribution-id:4}")
    private Long englishContributionId;

    @Value("${quran.translation.urdu-contribution-id:1}")
    private Long urduContributionId;

    private final TranslationRepository translationRepository;

    @Value("${quran.search.candidate-pool:80}")
    private int candidatePool;

    @Value("${quran.search.core-concept-weight:10.0}")
    private double coreConceptWeight;

    @Value("${quran.search.related-concept-weight:2.5}")
    private double relatedConceptWeight;

    @Value("${quran.search.phrase-bonus:8.0}")
    private double phraseBonus;

    @Value("${quran.search.fulltext-weight:0.4}")
    private double fulltextWeight;

    private static final Pattern BOOLEAN_UNSAFE = Pattern.compile("[+\\-><()~*\"@]");

    public List<AyatDto> search(KeywordExpansionDto expansion, int limit) {
        String strictQuery = buildBooleanQuery(expansion, true);
        if (strictQuery.isBlank()) {
            log.warn("Empty boolean query built from expansion");
            return List.of();
        }

        log.debug("FULLTEXT (strict): {}", strictQuery);
        List<AyatMatch> candidates =
                translationRepository.fullTextSearch(strictQuery, englishContributionId, candidatePool);
        log.debug("Strict pass returned {} candidates", candidates.size());

        if (candidates.size() < limit * 2) {
            String relaxed = buildBooleanQuery(expansion, false);
            log.debug("FULLTEXT (relaxed): {}", relaxed);
            List<AyatMatch> more =
                    translationRepository.fullTextSearch(relaxed, englishContributionId, candidatePool);
            candidates = mergeById(candidates, more);
            log.debug("After relaxed pass: {} candidates", candidates.size());
        }

        if (candidates.isEmpty()) {
            log.info("Fulltext empty, LIKE fallback");
            candidates = likeFallback(expansion);
        }

        List<AyatDto> ranked = rerank(candidates, expansion, limit);
        attachUrdu(ranked);                                   // <-- NEW

        ranked.forEach(a -> log.debug("  {} score={} concepts={} urdu={}",
                a.getReference(), String.format("%.2f", a.getRelevanceScore()),
                a.getMatchedConcepts(), a.getTranslationUrdu() != null));
        return ranked;
    }

    private String buildBooleanQuery(KeywordExpansionDto expansion, boolean strict) {
        StringJoiner sj = new StringJoiner(" ");
        Set<String> emitted = new HashSet<>();

        for (String phrase : expansion.getPhrases()) {
            String clean = sanitize(phrase);
            if (!clean.isBlank() && emitted.add(clean)) sj.add(">\"" + clean + "\"");
        }

        for (KeywordExpansionDto.ConceptGroup group : expansion.getConcepts()) {
            boolean core = group.isCore();

            for (String term : group.getTerms()) {
                String clean = sanitize(term);
                if (clean.length() < 3 || !emitted.add(clean)) continue;

                if (clean.contains(" ")) {
                    sj.add("\"" + clean + "\"");
                } else if (QuranTextUtils.LOW_VALUE_TERMS.contains(clean)) {
                    sj.add("<" + clean);
                } else if (core && strict) {
                    sj.add(">" + clean);
                } else {
                    sj.add(clean);
                }
            }


            for (String wildcard : QuranTextUtils.prefixWildcards(group.getTerms())) {
                if (emitted.add(wildcard)) sj.add(core ? ">" + wildcard : wildcard);
            }

            if (group.getTerms().size() <= 2) {
                for (String term : group.getTerms()) {
                    String prefix = QuranTextUtils.wildcardPrefix(sanitize(term));
                    if (!prefix.isBlank() && emitted.add(prefix + "*")) {
                        sj.add(core ? ">" + prefix + "*" : prefix + "*");
                    }
                }
            }
        }
        return sj.toString();
    }

    private String sanitize(String term) {
        return BOOLEAN_UNSAFE.matcher(term.toLowerCase())
                .replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private List<AyatMatch> mergeById(List<AyatMatch> a, List<AyatMatch> b) {
        Map<Long, AyatMatch> merged = new LinkedHashMap<>();
        a.forEach(m -> merged.put(m.getId(), m));
        b.forEach(m -> merged.putIfAbsent(m.getId(), m));
        return new ArrayList<>(merged.values());
    }

    private List<AyatMatch> likeFallback(KeywordExpansionDto expansion) {
        Map<Long, AyatMatch> merged = new LinkedHashMap<>();
        expansion.coreConcepts().stream()
                .flatMap(c -> c.getTerms().stream())
                .limit(6)
                .forEach(term -> translationRepository.likeSearch(term, englishContributionId, 20)
                        .forEach(m -> merged.putIfAbsent(m.getId(), m)));
        return new ArrayList<>(merged.values());
    }

    private void attachUrdu(List<AyatDto> ayats) {
        if (ayats.isEmpty()) return;

        List<Integer> keys = ayats.stream()
                .map(a -> a.getSurahNo() * 1000 + a.getAyatNo())
                .distinct()
                .toList();

        try {
            Map<Integer, String> urduByKey = translationRepository
                    .findByContributionAndKeys(urduContributionId, keys).stream()
                    .collect(Collectors.toMap(
                            r -> r.getSurahNo() * 1000 + r.getAyatNo(),
                            TranslationRow::getTranslation,
                            (a, b) -> a));

            for (AyatDto a : ayats) {
                a.setTranslationUrdu(urduByKey.get(a.getSurahNo() * 1000 + a.getAyatNo()));
            }

            long missing = ayats.stream().filter(a -> a.getTranslationUrdu() == null).count();
            if (missing > 0) {
                log.warn("{} of {} ayats have no Urdu translation (contribution_id={})",
                        missing, ayats.size(), urduContributionId);
            }
        } catch (Exception e) {
            // Urdu is supplementary — never fail the whole search over it
            log.error("Failed to attach Urdu translations", e);
        }
    }

    private List<AyatDto> rerank(List<AyatMatch> candidates, KeywordExpansionDto expansion, int limit) {
        int coreTotal = Math.max(1, expansion.coreConcepts().size());

        List<AyatDto> scored = new ArrayList<>();
        for (AyatMatch m : candidates) {
            String lower = m.getTranslation().toLowerCase();
            Set<String> verseStems  = QuranTextUtils.stemmedTokens(m.getTranslation());
            Set<String> verseTokens = QuranTextUtils.rawTokens(m.getTranslation());
            int wordCount = Math.max(1, lower.split("\\s+").length);

            List<String> matchedConcepts = new ArrayList<>();
            double conceptScore = 0;

            for (KeywordExpansionDto.ConceptGroup group : expansion.getConcepts()) {
                boolean hit = conceptMatches(group, lower, verseStems, verseTokens);
                if (hit) {
                    matchedConcepts.add(group.getConcept());
                    conceptScore += group.isCore() ? coreConceptWeight : relatedConceptWeight;
                }
            }

            if (matchedConcepts.isEmpty()) continue;   // fulltext noise

            double phraseScore = expansion.getPhrases().stream()
                    .filter(lower::contains).count() * phraseBonus;

            double ft = m.getScore() != null ? m.getScore() : 0.0;

            // reward concentration: short verse hitting the concepts beats a long rambling one
            double density = conceptScore / Math.sqrt(wordCount);

            double total = conceptScore + phraseScore + (ft * fulltextWeight) + density;

            long coreHits = expansion.coreConcepts().stream()
                    .filter(c -> matchedConcepts.contains(c.getConcept())).count();
            if (coreHits == 0) total *= 0.35;                       // related-only = weak
            if (coreHits == coreTotal && coreTotal > 1) total *= 1.4; // covered everything

            scored.add(AyatDto.builder()
                    .id(m.getId())
                    .surahNo(m.getSurahNo())
                    .ayatNo(m.getAyatNo())
                    .translation(m.getTranslation())
                    .relevanceScore(Math.round(total * 100) / 100.0)
                    .keywordHits(matchedConcepts.size())
                    .matchedConcepts(matchedConcepts)
                    .build());
        }

        scored.sort(Comparator.comparingDouble(AyatDto::getRelevanceScore).reversed());
        return scored.stream().limit(limit).toList();
    }
    /** Mirrors the retrieval-side matching, including the truncation wildcard. */
    private boolean conceptMatches(KeywordExpansionDto.ConceptGroup group, String lowerVerse,
                                   Set<String> verseStems, Set<String> verseTokens) {

        for (String term : group.getTerms()) {
            if (term.contains(" ")) {
                if (lowerVerse.contains(term)) return true;
            } else if (verseStems.contains(QuranTextUtils.stem(term))) {
                return true;
            }
        }

        // same >= 2 condition as buildBooleanQuery, so behaviour stays symmetric
        if (group.getTerms().size() <= 2) {
            for (String term : group.getTerms()) {
                String prefix = QuranTextUtils.wildcardPrefix(term);
                if (prefix.isBlank()) continue;
                for (String token : verseTokens) {
                    if (token.startsWith(prefix)) return true;
                }
            }
        }
        return false;
    }
}