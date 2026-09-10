package com.example.aiquran.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroqResponseDto {

    private String id;
    private String model;
    private List<Choice> choices;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Integer index;
        private Message message;
        private String finishReason;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;
    }

    public String getFirstText() {
        if (choices == null || choices.isEmpty()) return null;
        Choice choice = choices.get(0);
        if (choice == null || choice.getMessage() == null) return null;
        return choice.getMessage().getContent();
    }

    public String getFinishReason() {
        return (choices == null || choices.isEmpty()) ? null : choices.get(0).getFinishReason();
    }
}
