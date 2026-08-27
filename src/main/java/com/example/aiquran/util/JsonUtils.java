package com.example.aiquran.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtils {

    private JsonUtils() {}

    private static final Pattern FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    /** Strips markdown fences or surrounding prose that Gemini sometimes adds. */
    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        String trimmed = raw.trim();

        Matcher fence = FENCE.matcher(trimmed);
        if (fence.find()) return fence.group(1).trim();

        Matcher obj = OBJECT.matcher(trimmed);
        return obj.find() ? obj.group() : trimmed;
    }
}