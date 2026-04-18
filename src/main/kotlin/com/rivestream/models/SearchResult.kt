package com.rivestream.models

data class SearchResult(
    val tmdbId: Int,
    val title: String,
    val type: ContentType,
    val year: Int? = null,
    val language: String = "en",
)
