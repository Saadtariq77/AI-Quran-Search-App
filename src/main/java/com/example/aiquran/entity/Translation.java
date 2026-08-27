package com.example.aiquran.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "translations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contribution_id")
    private Long contributionId;

    @Column(name = "surah_no", nullable = false)
    private Integer surahNo;

    @Column(name = "ayat_no", nullable = false)
    private Integer ayatNo;

    @Column(name = "translation", columnDefinition = "TEXT", nullable = false)
    private String translation;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
