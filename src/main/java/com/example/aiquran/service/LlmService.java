package com.example.aiquran.service;

public interface LlmService {
    String generateContent(String systemPrompt, String userPrompt);
    String generateJson(String systemPrompt, String userPrompt);
}
