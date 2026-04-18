package com.rivestream.models

data class StreamLink(
    val quality: String,
    val language: String,
    val url: String,
    val isDownload: Boolean = false
)
