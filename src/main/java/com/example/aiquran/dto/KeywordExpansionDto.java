package com.example.aiquran.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeywordExpansionDto {

    private String detectedLanguage;
    private List<String> themes;
    private List<ConceptGroup> concepts;
    private List<String> phrases;

    @Getter(AccessLevel.NONE)
    @Builder.Default
    private Boolean degraded = false;

    public boolean isDegraded() {
        return Boolean.TRUE.equals(degraded);
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConceptGroup {
        private String concept;
        private String importance;
        private List<String> terms;

        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isCore() {
            return !"related".equalsIgnoreCase(importance);
        }
    }

    public List<String> allTerms() {
        if (concepts == null) return List.of();
        return concepts.stream().flatMap(c -> c.getTerms().stream()).distinct().toList();
    }

    public List<ConceptGroup> coreConcepts() {
        if (concepts == null) return List.of();
        return concepts.stream().filter(ConceptGroup::isCore).toList();
    }
}