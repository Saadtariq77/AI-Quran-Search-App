package com.example.aiquran.service;

import com.example.aiquran.exception.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Primary
@Service
public class RoutingLlmService implements LlmService {

    private final LlmService groqService;
    private final LlmService geminiService;

    @Value("${llm.provider:groq}")
    private String provider;

    public RoutingLlmService(
            @Qualifier("groqService") LlmService groqService,
            @Qualifier("geminiService") LlmService geminiService) {
        this.groqService = groqService;
        this.geminiService = geminiService;
    }

    @Override
    public String generateContent(String systemPrompt, String userPrompt) {
        if ("gemini".equalsIgnoreCase(provider)) {
            return geminiService.generateContent(systemPrompt, userPrompt);
        }

        try {
            return groqService.generateContent(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("Groq unavailable ({}); falling back to Gemini for generateContent", e.getMessage());
            return geminiService.generateContent(systemPrompt, userPrompt);
        }
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        if ("gemini".equalsIgnoreCase(provider)) {
            return geminiService.generateJson(systemPrompt, userPrompt);
        }

        try {
            return groqService.generateJson(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("Groq unavailable ({}); falling back to Gemini for generateJson", e.getMessage());
            return geminiService.generateJson(systemPrompt, userPrompt);
        }
    }
}
