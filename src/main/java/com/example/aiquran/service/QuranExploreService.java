package com.example.aiquran.service;

import com.example.aiquran.dto.*;
import com.example.aiquran.exception.LlmUnavailableException;
import com.example.aiquran.repository.TranslationRepository;
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
public class QuranExploreService {

    private final LlmService llmService;
    private final TranslationRepository translationRepository;
    private final ObjectMapper objectMapper;

    @Value("${quran.translation.english-contribution-id:4}")
    private Long englishContributionId;

    @Value("${quran.translation.urdu-contribution-id:1}")
    private Long urduContributionId;

    private static final String EXPLORE_SYSTEM_PROMPT = """
            You are a verified, authentic Islamic scholar and Quranic research assistant.
            You receive a specific verse of the Holy Quran (Surah number, Ayat number, Surah name, and translation).

            Your task is to provide an academically rigorous, authentic exploration of this verse:
            1. "explanation": A clear, insightful explanation of the verse's meanings, core themes, and practical life applications (2 to 3 paragraphs).
            2. "historicalContext": The authentic historical circumstances and occasion of revelation (Asbab al-Nuzul), citing the specific classical Tafsir work where it is recorded.
               HARD RULE: If classical scholars note that this verse has NO specific singular occasion of revelation, you MUST state that it was revealed as foundational guidance during the [Meccan/Medinan] period rather than inventing a backstory.
            3. "relatedHadiths": 1 to 2 authentic Hadiths from recognized Sunnah collections (Sahih al-Bukhari, Sahih Muslim, Sunan Abi Dawud, Jami' at-Tirmidhi, Sunan an-Nasa'i, Sunan Ibn Majah, Musnad Ahmad) that directly explain, contextualize, or apply this verse.
               HARD RULE: NEVER invent, fabricate, or hallucinate Hadiths, false narrators, or speculative Hadith numbers. If uncertain of an exact number, cite the collection and chapter name (e.g. "Sahih al-Bukhari, Kitab al-Tafsir").
            4. "authenticSources": A list of classical books cited (e.g., "Tafsir Ibn Kathir", "Tafsir al-Tabari", "Sahih al-Bukhari").

            Return STRICT JSON only conforming to the schema below. No markdown fences, no text outside the JSON.

            SCHEMA
            {
              "explanation": "Clear explanation of verse meaning and practical lessons",
              "historicalContext": {
                "event": "Brief title of the historical event or 'Foundational Guidance'",
                "tafsirSource": "e.g. Tafsir Ibn Kathir / Tafsir al-Tabari",
                "details": "Factual context from verified classical works"
              },
              "relatedHadiths": [
                {
                  "narrator": "e.g. Narrated by Abu Hurairah (RA)",
                  "hadithText": "English text of the narration",
                  "sourceBook": "e.g. Sahih al-Bukhari, Book of Tafsir",
                  "connection": "How this hadith explains or applies to this verse"
                }
              ],
              "authenticSources": [
                "Tafsir Ibn Kathir",
                "Sahih al-Bukhari"
              ]
            }
            """;

    public AyatExploreResponse explore(AyatExploreRequest request) {
        long start = System.currentTimeMillis();

        if (request.getSurahNo() == null || request.getAyatNo() == null) {
            throw new IllegalArgumentException("surahNo and ayatNo must not be null");
        }

        int surahNo = request.getSurahNo();
        int ayatNo = request.getAyatNo();
        String ref = surahNo + ":" + ayatNo;
        String[] surahNames = SURAH_NAMES.getOrDefault(surahNo, new String[]{"Surah " + surahNo, ""});
        String surahEn = surahNames[0];
        String surahAr = surahNames[1];

        String translationEn = request.getTranslation();
        String translationUr = request.getTranslationUrdu();

        if (translationEn == null || translationEn.isBlank() || translationUr == null || translationUr.isBlank()) {
            int key = surahNo * 1000 + ayatNo;
            try {
                if (translationEn == null || translationEn.isBlank()) {
                    List<TranslationRow> enRows = translationRepository.findByContributionAndKeys(englishContributionId, List.of(key));
                    if (!enRows.isEmpty()) {
                        translationEn = enRows.get(0).getTranslation();
                    }
                }
                if (translationUr == null || translationUr.isBlank()) {
                    List<TranslationRow> urRows = translationRepository.findByContributionAndKeys(urduContributionId, List.of(key));
                    if (!urRows.isEmpty()) {
                        translationUr = urRows.get(0).getTranslation();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load translations from DB for {}: {}", ref, e.getMessage());
            }
        }

        String explanation = null;
        HistoricalContextDto historicalContext = null;
        List<HadithReferenceDto> relatedHadiths = new ArrayList<>();
        List<String> authenticSources = new ArrayList<>();
        boolean degraded = false;

        try {
            String userPrompt = buildUserPrompt(surahNo, ayatNo, surahEn, translationEn);
            String rawJson = llmService.generateJson(EXPLORE_SYSTEM_PROMPT, userPrompt);
            log.debug("Explore AI raw response for {}: {}", ref, rawJson);

            LlmExploreResult result = objectMapper.readValue(
                    JsonUtils.extractJsonObject(rawJson), LlmExploreResult.class);

            explanation = result.getExplanation();
            historicalContext = result.getHistoricalContext();
            if (result.getRelatedHadiths() != null) {
                relatedHadiths = result.getRelatedHadiths();
            }
            if (result.getAuthenticSources() != null) {
                authenticSources = result.getAuthenticSources();
            }
        } catch (LlmUnavailableException e) {
            log.warn("LLM unavailable for exploring {}: {}", ref, e.getMessage());
            degraded = true;
            explanation = "AI explanation service is temporarily unavailable. Please try again shortly.";
        } catch (Exception e) {
            log.error("Failed to explore ayat {}", ref, e);
            degraded = true;
            explanation = "Unable to generate detailed explanation at this time. Please try again shortly.";
        }

        return AyatExploreResponse.builder()
                .surahNo(surahNo)
                .ayatNo(ayatNo)
                .reference(ref)
                .surahNameEn(surahEn)
                .surahNameAr(surahAr)
                .translation(translationEn)
                .translationUrdu(translationUr)
                .explanation(explanation)
                .historicalContext(historicalContext)
                .relatedHadiths(relatedHadiths)
                .authenticSources(authenticSources)
                .degraded(degraded)
                .tookMs(System.currentTimeMillis() - start)
                .build();
    }

    private String buildUserPrompt(int surahNo, int ayatNo, String surahEn, String translation) {
        return String.format("""
                Verse to explore:
                Surah: %d (%s), Ayat: %d
                English Translation: "%s"

                Provide the authentic Tafsir explanation, historical context / Asbab al-Nuzul with book citation, and related Hadiths with references.
                Return the JSON object.
                """, surahNo, surahEn, ayatNo, translation != null ? translation : "N/A");
    }

    @lombok.Data
    public static class LlmExploreResult {
        private String explanation;
        private HistoricalContextDto historicalContext;
        private List<HadithReferenceDto> relatedHadiths;
        private List<String> authenticSources;
    }

    private static final Map<Integer, String[]> SURAH_NAMES = new HashMap<>();

    static {
        SURAH_NAMES.put(1, new String[]{"Al-Fatihah", "الفاتحة"});
        SURAH_NAMES.put(2, new String[]{"Al-Baqarah", "البقرة"});
        SURAH_NAMES.put(3, new String[]{"Aal-e-Imran", "آل عمران"});
        SURAH_NAMES.put(4, new String[]{"An-Nisa", "النساء"});
        SURAH_NAMES.put(5, new String[]{"Al-Ma'idah", "المائدة"});
        SURAH_NAMES.put(6, new String[]{"Al-An'am", "الأنعام"});
        SURAH_NAMES.put(7, new String[]{"Al-A'raf", "الأعراف"});
        SURAH_NAMES.put(8, new String[]{"Al-Anfal", "الأنفال"});
        SURAH_NAMES.put(9, new String[]{"At-Tawbah", "التوبة"});
        SURAH_NAMES.put(10, new String[]{"Yunus", "يونس"});
        SURAH_NAMES.put(11, new String[]{"Hud", "هود"});
        SURAH_NAMES.put(12, new String[]{"Yusuf", "يوسف"});
        SURAH_NAMES.put(13, new String[]{"Ar-Ra'd", "الرعد"});
        SURAH_NAMES.put(14, new String[]{"Ibrahim", "ابراهيم"});
        SURAH_NAMES.put(15, new String[]{"Al-Hijr", "الحجر"});
        SURAH_NAMES.put(16, new String[]{"An-Nahl", "النحل"});
        SURAH_NAMES.put(17, new String[]{"Al-Isra", "الإسراء"});
        SURAH_NAMES.put(18, new String[]{"Al-Kahf", "الكهف"});
        SURAH_NAMES.put(19, new String[]{"Maryam", "مريم"});
        SURAH_NAMES.put(20, new String[]{"Ta-Ha", "طه"});
        SURAH_NAMES.put(21, new String[]{"Al-Anbiya", "الأنبياء"});
        SURAH_NAMES.put(22, new String[]{"Al-Hajj", "الحج"});
        SURAH_NAMES.put(23, new String[]{"Al-Mu'minun", "المؤمنون"});
        SURAH_NAMES.put(24, new String[]{"An-Nur", "النور"});
        SURAH_NAMES.put(25, new String[]{"Al-Furqan", "الفرقان"});
        SURAH_NAMES.put(26, new String[]{"Ash-Shu'ara", "الشعراء"});
        SURAH_NAMES.put(27, new String[]{"An-Naml", "النمل"});
        SURAH_NAMES.put(28, new String[]{"Al-Qasas", "القصص"});
        SURAH_NAMES.put(29, new String[]{"Al-Ankabut", "العنكبوت"});
        SURAH_NAMES.put(30, new String[]{"Ar-Rum", "الروم"});
        SURAH_NAMES.put(31, new String[]{"Luqman", "لقمان"});
        SURAH_NAMES.put(32, new String[]{"As-Sajdah", "السجدة"});
        SURAH_NAMES.put(33, new String[]{"Al-Ahzab", "الأحزاب"});
        SURAH_NAMES.put(34, new String[]{"Saba", "سبإ"});
        SURAH_NAMES.put(35, new String[]{"Fatir", "فاطر"});
        SURAH_NAMES.put(36, new String[]{"Ya-Sin", "يس"});
        SURAH_NAMES.put(37, new String[]{"As-Saffat", "الصافات"});
        SURAH_NAMES.put(38, new String[]{"Sad", "ص"});
        SURAH_NAMES.put(39, new String[]{"Az-Zumar", "الزمر"});
        SURAH_NAMES.put(40, new String[]{"Ghafir", "غافر"});
        SURAH_NAMES.put(41, new String[]{"Fussilat", "فصلت"});
        SURAH_NAMES.put(42, new String[]{"Ash-Shura", "الشورى"});
        SURAH_NAMES.put(43, new String[]{"Az-Zukhruf", "الزخرف"});
        SURAH_NAMES.put(44, new String[]{"Ad-Dukhan", "الدخان"});
        SURAH_NAMES.put(45, new String[]{"Al-Jathiyah", "الجاثية"});
        SURAH_NAMES.put(46, new String[]{"Al-Ahqaf", "الأحقاف"});
        SURAH_NAMES.put(47, new String[]{"Muhammad", "محمد"});
        SURAH_NAMES.put(48, new String[]{"Al-Fath", "الفتح"});
        SURAH_NAMES.put(49, new String[]{"Al-Hujurat", "الحجرات"});
        SURAH_NAMES.put(50, new String[]{"Qaf", "ق"});
        SURAH_NAMES.put(51, new String[]{"Adh-Dhariyat", "الذاريات"});
        SURAH_NAMES.put(52, new String[]{"At-Tur", "الطور"});
        SURAH_NAMES.put(53, new String[]{"An-Najm", "النجم"});
        SURAH_NAMES.put(54, new String[]{"Al-Qamar", "القمر"});
        SURAH_NAMES.put(55, new String[]{"Ar-Rahman", "الرحمن"});
        SURAH_NAMES.put(56, new String[]{"Al-Waqi'ah", "الواقعة"});
        SURAH_NAMES.put(57, new String[]{"Al-Hadid", "الحديد"});
        SURAH_NAMES.put(58, new String[]{"Al-Mujadila", "المجادلة"});
        SURAH_NAMES.put(59, new String[]{"Al-Hashr", "الحشر"});
        SURAH_NAMES.put(60, new String[]{"Al-Mumtahanah", "الممتحنة"});
        SURAH_NAMES.put(61, new String[]{"As-Saf", "الصف"});
        SURAH_NAMES.put(62, new String[]{"Al-Jumu'ah", "الجمعة"});
        SURAH_NAMES.put(63, new String[]{"Al-Munafiqun", "المنافقون"});
        SURAH_NAMES.put(64, new String[]{"At-Taghabun", "التغابن"});
        SURAH_NAMES.put(65, new String[]{"At-Talaq", "الطلاق"});
        SURAH_NAMES.put(66, new String[]{"At-Tahrim", "التحريم"});
        SURAH_NAMES.put(67, new String[]{"Al-Mulk", "الملك"});
        SURAH_NAMES.put(68, new String[]{"Al-Qalam", "القلم"});
        SURAH_NAMES.put(69, new String[]{"Al-Haqqah", "الحاقة"});
        SURAH_NAMES.put(70, new String[]{"Al-Ma'arij", "المعارج"});
        SURAH_NAMES.put(71, new String[]{"Nuh", "نوح"});
        SURAH_NAMES.put(72, new String[]{"Al-Jinn", "الجن"});
        SURAH_NAMES.put(73, new String[]{"Al-Muzzammil", "المزمل"});
        SURAH_NAMES.put(74, new String[]{"Al-Muddaththir", "المدثر"});
        SURAH_NAMES.put(75, new String[]{"Al-Qiyamah", "القيامة"});
        SURAH_NAMES.put(76, new String[]{"Al-Insan", "الانسان"});
        SURAH_NAMES.put(77, new String[]{"Al-Mursalat", "المرسلات"});
        SURAH_NAMES.put(78, new String[]{"An-Naba", "النبإ"});
        SURAH_NAMES.put(79, new String[]{"An-Nazi'at", "النازعات"});
        SURAH_NAMES.put(80, new String[]{"Abasa", "عبس"});
        SURAH_NAMES.put(81, new String[]{"At-Takwir", "التكوير"});
        SURAH_NAMES.put(82, new String[]{"Al-Infitar", "الإنفطار"});
        SURAH_NAMES.put(83, new String[]{"Al-Mutaffifin", "المطففين"});
        SURAH_NAMES.put(84, new String[]{"Al-Inshiqaq", "الإنشقاق"});
        SURAH_NAMES.put(85, new String[]{"Al-Buruj", "البروج"});
        SURAH_NAMES.put(86, new String[]{"At-Tariq", "الطارق"});
        SURAH_NAMES.put(87, new String[]{"Al-A'la", "الأعلى"});
        SURAH_NAMES.put(88, new String[]{"Al-Ghashiyah", "الغاشية"});
        SURAH_NAMES.put(89, new String[]{"Al-Fajr", "الفجر"});
        SURAH_NAMES.put(90, new String[]{"Al-Balad", "البلد"});
        SURAH_NAMES.put(91, new String[]{"Ash-Shams", "الشمس"});
        SURAH_NAMES.put(92, new String[]{"Al-Layl", "الليل"});
        SURAH_NAMES.put(93, new String[]{"Ad-Duhaa", "الضحى"});
        SURAH_NAMES.put(94, new String[]{"Ash-Sharh", "الشرح"});
        SURAH_NAMES.put(95, new String[]{"At-Tin", "التين"});
        SURAH_NAMES.put(96, new String[]{"Al-Alaq", "العلق"});
        SURAH_NAMES.put(97, new String[]{"Al-Qadr", "القدر"});
        SURAH_NAMES.put(98, new String[]{"Al-Bayyinah", "البينة"});
        SURAH_NAMES.put(99, new String[]{"Az-Zalzalah", "الزلزلة"});
        SURAH_NAMES.put(100, new String[]{"Al-Adiyat", "العاديات"});
        SURAH_NAMES.put(101, new String[]{"Al-Qari'ah", "القارعة"});
        SURAH_NAMES.put(102, new String[]{"At-Takathur", "التكاثر"});
        SURAH_NAMES.put(103, new String[]{"Al-Asr", "العصر"});
        SURAH_NAMES.put(104, new String[]{"Al-Humazah", "الهمزة"});
        SURAH_NAMES.put(105, new String[]{"Al-Fil", "الفيل"});
        SURAH_NAMES.put(106, new String[]{"Quraysh", "قريش"});
        SURAH_NAMES.put(107, new String[]{"Al-Ma'un", "الماعون"});
        SURAH_NAMES.put(108, new String[]{"Al-Kawthar", "الکوثر"});
        SURAH_NAMES.put(109, new String[]{"Al-Kafirun", "الكافرون"});
        SURAH_NAMES.put(110, new String[]{"An-Nasr", "النصر"});
        SURAH_NAMES.put(111, new String[]{"Al-Masad", "المسد"});
        SURAH_NAMES.put(112, new String[]{"Al-Ikhlas", "الإخلاص"});
        SURAH_NAMES.put(113, new String[]{"Al-Falaq", "الفلق"});
        SURAH_NAMES.put(114, new String[]{"An-Nas", "الناس"});
    }
}
