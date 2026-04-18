package com.rivestream.models

data class Movie(
    val tmdbId: Int,
    val title: String,
    val year: Int? = null,
    val language: String = "en",
)
