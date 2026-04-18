package com.rivestream.models

data class TVShow(
    val tmdbId: Int,
    val title: String,
    val year: Int? = null,
    val language: String = "en",
    val seasons: Int? = null,
)
