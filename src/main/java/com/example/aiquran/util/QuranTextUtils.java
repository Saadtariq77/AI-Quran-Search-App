package com.example.aiquran.util;



import java.util.*;

public final class QuranTextUtils {

    private QuranTextUtils() {}

    /** Query-intent noise. Never useful as a search term. */
    public static final Set<String> STOP_WORDS = Set.of(
            "i","me","my","mine","myself","we","our","us","you","your","he","she","it","they","them","their",
            "a","an","the","and","or","but","if","of","at","by","for","with","about","into","through","to",
            "from","in","on","off","over","under","is","am","are","was","were","be","been","being","have",
            "has","had","do","does","did","doing","will","would","shall","should","can","could","may","might",
            "must","this","that","these","those","there","here","then","than","so","very","just","not","no",
            "want","wants","wanted","need","needs","needed","wish","wishes","looking","look","search",
            "searching","find","finding","tell","show","give","get","help","please","kindly","know","make",
            "made","thing","things","something","anything","everything","way","ways","how","what","why",
            "when","where","who","which","some","any","all","more","most","much","many","also","really",
            "quran","quranic","verse","verses","ayat","ayah","ayats","surah","sura","chapter","islam",
            "islamic","muslim","muslims","tell me","related","relevant","regarding","concerning"
    );

    /**
     * Terms that appear in thousands of verses. Not removed — de-weighted with '<'
     * so they can still break ties without dominating the ranking.
     */
    public static final Set<String> LOW_VALUE_TERMS = Set.of(
            "allah","god","lord","indeed","verily","said","say","says","people","man","men","earth",
            "heaven","heavens","day","night","believe","believers","disbelievers","truth","book",
            "messenger","prophet","sign","signs","life","world","time","great","every","among"
    );

    /** Longest suffix first. Light stemmer — good enough for grouping word families. */
    private static final String[][] SUFFIX_RULES = {
            {"iveness",""},{"fulness",""},{"ousness",""},{"ization","ize"},{"ations","ate"},
            {"ation","ate"},{"iness","y"},{"ments",""},{"ment",""},{"ness",""},
            {"ings",""},{"ies","y"},{"ied","y"},{"ing",""},{"edly",""},
            {"ally",""},{"ance",""},{"ence",""},{"able",""},{"ible",""},
            {"ful",""},{"less",""},{"ely",""},{"ers",""},{"est",""},
            {"ly",""},{"er",""},{"ed",""},{"es",""},{"s",""}
    };

    private static final int MIN_STEM_LENGTH = 3;

    public static String stem(String word) {
        if (word == null) return "";
        String w = word.toLowerCase().trim();
        if (w.length() <= 3) return w;

        for (String[] rule : SUFFIX_RULES) {
            String suffix = rule[0];
            if (w.endsWith(suffix) && w.length() - suffix.length() >= MIN_STEM_LENGTH) {
                w = w.substring(0, w.length() - suffix.length()) + rule[1];
                break;
            }
        }
        // running -> runn -> run
        int len = w.length();
        if (len > 3 && w.charAt(len - 1) == w.charAt(len - 2) && "bdfglmnprt".indexOf(w.charAt(len - 1)) >= 0) {
            w = w.substring(0, len - 1);
        }
        return w;
    }

    public static final int WILDCARD_PREFIX_LEN = 5;

    /**
     * Truncation prefix for a FULLTEXT wildcard on a single term.
     * "marriage" -> "marri" (matches marriage, married, marries, marrying).
     * Returns "" for words too short to truncate safely.
     */
    public static String wildcardPrefix(String term) {
        if (term == null) return "";
        String t = term.toLowerCase().trim();
        if (t.length() < 4 || !t.matches("[a-z]+")) return "";
        return t.substring(0, Math.min(WILDCARD_PREFIX_LEN, t.length()));
    }

    /** Raw lowercase tokens (unstemmed) — needed for prefix matching in rerank. */
    public static Set<String> rawTokens(String text) {
        Set<String> out = new HashSet<>();
        for (String raw : text.toLowerCase().split("[^a-z']+")) {
            String t = raw.replace("'", "");
            if (t.length() >= 3) out.add(t);
        }
        return out;
    }

    public static Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String raw : text.toLowerCase().split("[^a-z']+")) {
            String t = raw.replace("'", "");
            if (t.length() >= 3 && !STOP_WORDS.contains(t)) out.add(t);
        }
        return out;
    }

    public static boolean isUseful(String term) {
        if (term == null) return false;
        String t = term.toLowerCase().trim();
        return t.length() >= 3 && !STOP_WORDS.contains(t);
    }

    /** Stem a whole verse once so reranking can do stem-level matching. */
    public static Set<String> stemmedTokens(String text) {
        Set<String> out = new HashSet<>();
        for (String raw : text.toLowerCase().split("[^a-z']+")) {
            if (raw.length() >= 3) out.add(stem(raw));
        }
        return out;
    }

    /**
     * Cluster related terms and emit FULLTEXT prefix wildcards.
     * ["marriage","married","marries","marrying"] -> "marri*"
     * Catches inflections Gemini didn't list explicitly.
     */
    public static Set<String> prefixWildcards(Collection<String> terms) {
        List<String> clean = terms.stream()
                .map(t -> t.toLowerCase().trim())
                .filter(t -> t.length() >= 5 && t.matches("[a-z]+"))
                .distinct()
                .sorted()
                .toList();

        Set<String> wildcards = new LinkedHashSet<>();
        Map<String, List<String>> byPrefix = new LinkedHashMap<>();
        for (String t : clean) {
            byPrefix.computeIfAbsent(t.substring(0, 4), k -> new ArrayList<>()).add(t);
        }

        for (List<String> cluster : byPrefix.values()) {
            if (cluster.size() < 2) continue;
            String lcp = longestCommonPrefix(cluster);
            if (lcp.length() >= 4) wildcards.add(lcp + "*");
        }
        return wildcards;
    }

    private static String longestCommonPrefix(List<String> words) {
        String prefix = words.get(0);
        for (String w : words) {
            int i = 0;
            while (i < prefix.length() && i < w.length() && prefix.charAt(i) == w.charAt(i)) i++;
            prefix = prefix.substring(0, i);
            if (prefix.isEmpty()) break;
        }
        return prefix;
    }
}
