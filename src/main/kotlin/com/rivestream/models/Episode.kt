package com.rivestream.models

data class Episode(
    val tmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val language: String = "en",
)
