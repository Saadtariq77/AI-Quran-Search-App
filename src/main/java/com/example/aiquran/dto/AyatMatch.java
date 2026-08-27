package com.example.aiquran.dto;


/** Native-query projection. Aliases in SQL must match these getters. */
public interface AyatMatch {

    Long getId();

    Integer getSurahNo();

    Integer getAyatNo();

    String getTranslation();

    Double getScore();
}