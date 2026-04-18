package com.rivestream

import java.io.IOException

sealed class RivestreamError(val userMessage: String) {
    data object Network : RivestreamError("Network error. Please check your internet connection.")

    data object InvalidResponse : RivestreamError("Received an invalid response from the server.")

    data object MissingTmdbId : RivestreamError("Unable to resolve TMDB ID for this title.")

    data class RateLimited(val retryAfterSeconds: Int?) :
        RivestreamError(
            if (retryAfterSeconds != null) {
                "Too many requests. Please try again in $retryAfterSeconds second${if (retryAfterSeconds == 1) "" else "s"}."
            } else {
                "Too many requests. Please try again shortly."
            },
        )

    data class Api(val message: String) : RivestreamError(message)
}

class RateLimitException(
    val retryAfterSeconds: Int? = null,
    message: String = "Rate limited",
) : IOException(message)

class ErrorHandler(
    private val maxRetries: Int = 2,
    private val defaultRetryDelayMillis: Long = 500L,
) {
    companion object {
        private const val MAX_RETRY_DELAY_MILLIS: Long = 60_000L
    }

    fun toError(throwable: Throwable): RivestreamError =
        when (throwable) {
            is RateLimitException -> RivestreamError.RateLimited(throwable.retryAfterSeconds)
            is IOException -> RivestreamError.Network
            is IllegalArgumentException -> RivestreamError.InvalidResponse
            else -> RivestreamError.Api(throwable.message ?: "Unexpected error occurred")
        }

    fun toUserMessage(throwable: Throwable): String = toError(throwable).userMessage

    fun <T> executeWithRetry(block: () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt <= maxRetries) {
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                val shouldRetry = error is IOException || error is RateLimitException
                if (!shouldRetry || attempt == maxRetries) {
                    throw error
                }

                val retryDelay = (error as? RateLimitException)?.retryAfterSeconds?.times(1000L)
                    ?: defaultRetryDelayMillis
                Thread.sleep(retryDelay.coerceIn(0L, MAX_RETRY_DELAY_MILLIS))
                attempt++
            }
        }

        throw lastError ?: IllegalStateException("Retry operation failed")
    }
}
