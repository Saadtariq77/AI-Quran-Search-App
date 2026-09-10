package com.example.aiquran.service;

import com.example.aiquran.dto.GroqRequestDto;
import com.example.aiquran.dto.GroqResponseDto;
import com.example.aiquran.exception.LlmUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service("groqService")
@RequiredArgsConstructor
public class GroqService implements LlmService {

    private final RestTemplate restTemplate;

    @Value("${groq.base-url:https://api.groq.com/openai}")
    private String baseUrl;

    @Value("${groq.api-key:}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String model;

    @Value("${groq.max-tokens:1500}")
    private Integer maxTokens;

    @Override
    public String generateContent(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, false, 0.7);
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, true, 0.2);
    }

    private String call(String systemPrompt, String userPrompt, boolean jsonMode, double temperature) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("gsk_placeholder")) {
            throw new LlmUnavailableException("Groq API key is not configured");
        }

        String endpoint = baseUrl.endsWith("/v1")
                ? baseUrl + "/chat/completions"
                : baseUrl + "/v1/chat/completions";

        List<GroqRequestDto.MessageDto> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(GroqRequestDto.MessageDto.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build());
        }
        messages.add(GroqRequestDto.MessageDto.builder()
                .role("user")
                .content(userPrompt)
                .build());

        GroqRequestDto.ResponseFormatDto responseFormat = jsonMode
                ? GroqRequestDto.ResponseFormatDto.builder().type("json_object").build()
                : null;

        GroqRequestDto requestDto = GroqRequestDto.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .responseFormat(responseFormat)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        HttpEntity<GroqRequestDto> entity = new HttpEntity<>(requestDto, headers);

        try {
            ResponseEntity<GroqResponseDto> response =
                    restTemplate.exchange(endpoint, HttpMethod.POST, entity, GroqResponseDto.class);

            GroqResponseDto body = response.getBody();
            String text = body != null ? body.getFirstText() : null;

            if (text != null && !text.isBlank()) {
                return text;
            }

            log.error("Empty Groq response (finishReason={})", body != null ? body.getFinishReason() : "null");
            throw new LlmUnavailableException("Groq returned no usable text");

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();

            log.warn("Groq request failed (HTTP {}): {}", status, summarise(responseBody));
            throw new LlmUnavailableException("Groq HTTP " + status + ": " + summarise(responseBody), e);

        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Groq API call failed", e);
            throw new LlmUnavailableException("Groq API error: " + e.getMessage(), e);
        }
    }

    private String summarise(String body) {
        if (body == null) return "(no body)";
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }
}
