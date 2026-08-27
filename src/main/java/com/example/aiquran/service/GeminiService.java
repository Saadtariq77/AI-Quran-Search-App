package com.example.aiquran.service;

import com.example.aiquran.dto.GeminiRequestDto;
import com.example.aiquran.dto.GeminiResponseDto;
import com.example.aiquran.exception.GeminiUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final RestTemplate restTemplate;

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    public String generateContent(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, false, 0.7);
    }

    public String generateJson(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, true, 0.2);
    }

    private String call(String systemPrompt, String userPrompt, boolean jsonMode, double temperature) {
        String url = String.format("%s/v1beta/models/%s:generateContent", baseUrl, model);

        GeminiRequestDto.GenerationConfigDto config = GeminiRequestDto.GenerationConfigDto.builder()
                .temperature(temperature)
                .maxOutputTokens(4096)
                .responseMimeType(jsonMode ? "application/json" : null)
                .thinkingConfig(GeminiRequestDto.ThinkingConfigDto.builder().thinkingBudget(0).build())
                .build();

        GeminiRequestDto requestDto = GeminiRequestDto.builder()
                .systemInstruction(GeminiRequestDto.ContentDto.builder()
                        .parts(List.of(GeminiRequestDto.PartDto.builder().text(systemPrompt).build()))
                        .build())
                .contents(List.of(
                        GeminiRequestDto.ContentDto.builder()
                                .role("user")
                                .parts(List.of(GeminiRequestDto.PartDto.builder().text(userPrompt).build()))
                                .build()
                ))
                .generationConfig(config)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-goog-api-key", apiKey);

        HttpEntity<GeminiRequestDto> entity = new HttpEntity<>(requestDto, headers);

        try {
            ResponseEntity<GeminiResponseDto> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, GeminiResponseDto.class);

            GeminiResponseDto body = response.getBody();
            String text = body != null ? body.getFirstText() : null;

            if (text != null && !text.isBlank()) {
                return text;
            }

            log.error("Empty Gemini response (finishReason={})", body != null ? body.getFinishReason() : "null");
            throw new GeminiUnavailableException("Gemini returned no usable text");

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();

            if (status == 429 || status >= 500) {
                log.warn("Gemini temporarily unavailable (HTTP {}): {}", status, summarise(responseBody));
                throw new GeminiUnavailableException("Gemini HTTP " + status);
            }

            log.error("Gemini API call failed HTTP {}: {}", status, responseBody);
            throw new RuntimeException("Gemini API error " + status + ": " + responseBody, e);

        } catch (GeminiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        }
    }

    private String summarise(String body) {
        if (body == null) return "(no body)";
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }
}