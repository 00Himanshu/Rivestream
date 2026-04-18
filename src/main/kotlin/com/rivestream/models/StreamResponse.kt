package com.rivestream.models

data class StreamResponse(
    val success: Boolean,
    val links: List<StreamLink> = emptyList(),
    val message: String? = null
)
