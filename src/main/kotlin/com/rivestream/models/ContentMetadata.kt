package com.rivestream.models

enum class ContentType {
    MOVIE,
    TVSHOW,
}

data class ContentMetadata(
    val tmdbId: Int,
    val title: String,
    val type: ContentType,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
)
