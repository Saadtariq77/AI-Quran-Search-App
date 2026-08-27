package com.example.aiquran.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponseDto {

    private List<Candidate> candidates;
    private PromptFeedback promptFeedback;

    @Getter @Setter @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Content content;
        private String finishReason;
    }

    @Getter @Setter @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private List<Part> parts;
        private String role;
    }

    @Getter @Setter @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        private String text;
        private Boolean thought;
    }

    @Getter @Setter @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptFeedback {
        private String blockReason;
    }

    /** Concatenates all non-thought text parts. Null if there is nothing usable. */
    public String getFirstText() {
        if (candidates == null || candidates.isEmpty()) return null;
        Content content = candidates.get(0).getContent();
        if (content == null || content.getParts() == null) return null;

        StringBuilder sb = new StringBuilder();
        for (Part p : content.getParts()) {
            if (Boolean.TRUE.equals(p.getThought())) continue;
            if (p.getText() != null) sb.append(p.getText());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public String getFinishReason() {
        return (candidates == null || candidates.isEmpty()) ? null : candidates.get(0).getFinishReason();
    }
}