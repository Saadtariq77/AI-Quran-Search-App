package com.example.aiquran.controller;


import com.example.aiquran.dto.KeywordExpansionDto;
import com.example.aiquran.dto.QuranSearchRequest;
import com.example.aiquran.dto.QuranSearchResponse;
import com.example.aiquran.service.QuranKeywordService;
import com.example.aiquran.service.QuranSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/quran")
@RequiredArgsConstructor
public class QuranSearchController {

    private final QuranSearchService quranSearchService;
    private final QuranKeywordService keywordService;

    @PostMapping("/search")
    public ResponseEntity<QuranSearchResponse> search(@RequestBody QuranSearchRequest request) {
        log.info("Quran search: {}", request.getQuery());
        return ResponseEntity.ok(quranSearchService.search(request));
    }

    @GetMapping("/search")
    public ResponseEntity<QuranSearchResponse> searchGet(@RequestParam String q,
                                                         @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(quranSearchService.search(
                QuranSearchRequest.builder().query(q).maxAyats(limit).build()));
    }

    @GetMapping("/debug/expand")
    public ResponseEntity<KeywordExpansionDto> debugExpand(@RequestParam String q) {
        return ResponseEntity.ok(keywordService.expand(q));
    }
}
