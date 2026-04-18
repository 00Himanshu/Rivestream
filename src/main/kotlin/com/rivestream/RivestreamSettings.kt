package com.rivestream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.prefs.Preferences

class RivestreamSettings(
    private val preferences: Preferences = Preferences.userNodeForPackage(RivestreamSettings::class.java),
) {
    companion object {
        private const val KEY_API_ENDPOINT = "api_endpoint"
        private const val KEY_DEFAULT_QUALITY = "default_quality"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_CACHE_MINUTES = "cache_minutes"

        private const val DEFAULT_QUALITY = "Auto"
        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_CACHE_MINUTES = 30

        val QUALITY_OPTIONS: List<String> = listOf("Auto", "1080p", "720p", "480p")
    }

    fun getState(): RivestreamSettingsState =
        RivestreamSettingsState(
            apiEndpoint = preferences.get(KEY_API_ENDPOINT, RivestreamAPI.DEFAULT_ENDPOINT),
            defaultQuality = preferences.get(KEY_DEFAULT_QUALITY, DEFAULT_QUALITY),
            language = preferences.get(KEY_LANGUAGE, DEFAULT_LANGUAGE),
            cacheEnabled = preferences.getBoolean(KEY_CACHE_ENABLED, true),
            cacheMinutes = preferences.getInt(KEY_CACHE_MINUTES, DEFAULT_CACHE_MINUTES).coerceAtLeast(1),
        )

    fun getSettingsUi(errorMessage: String? = null): SettingsUiModel {
        val state = getState()
        return SettingsUiModel(
            title = "Rivestream Settings",
            endpointLabel = "API Endpoint URL",
            endpointHint = RivestreamAPI.DEFAULT_ENDPOINT,
            endpointValue = state.apiEndpoint,
            qualityLabel = "Default quality",
            qualityOptions = QUALITY_OPTIONS,
            qualityValue = state.defaultQuality,
            languageLabel = "Language",
            languageHint = "en",
            languageValue = state.language,
            cacheLabel = "Enable cache",
            cacheEnabled = state.cacheEnabled,
            cacheDurationLabel = "Cache duration (minutes)",
            cacheDurationMinutes = state.cacheMinutes,
            validationError = errorMessage,
        )
    }

    fun updateApiEndpoint(endpointInput: String): SettingsUiModel {
        val normalized = normalizeEndpoint(endpointInput)
            ?: return getSettingsUi("Please enter a valid http/https endpoint URL")

        preferences.put(KEY_API_ENDPOINT, normalized)
        return getSettingsUi()
    }

    fun updateDefaultQuality(quality: String): SettingsUiModel {
        if (quality !in QUALITY_OPTIONS) {
            return getSettingsUi("Invalid quality option")
        }

        preferences.put(KEY_DEFAULT_QUALITY, quality)
        return getSettingsUi()
    }

    fun updateLanguage(language: String): SettingsUiModel {
        val normalized = language.trim().lowercase()
        val isValid = normalized.matches(Regex("^[a-z]{2}(-[a-z]{2})?$"))
        if (!isValid) {
            return getSettingsUi("Invalid language code. Use values like en or en-us")
        }

        preferences.put(KEY_LANGUAGE, normalized)
        return getSettingsUi()
    }

    fun updateCacheEnabled(enabled: Boolean): SettingsUiModel {
        preferences.putBoolean(KEY_CACHE_ENABLED, enabled)
        return getSettingsUi()
    }

    fun updateCacheMinutes(minutes: Int): SettingsUiModel {
        if (minutes <= 0) {
            return getSettingsUi("Cache duration must be greater than zero")
        }

        preferences.putInt(KEY_CACHE_MINUTES, minutes)
        return getSettingsUi()
    }

    private fun normalizeEndpoint(endpoint: String?): String? {
        val sanitized = endpoint?.trim().orEmpty().removeSuffix("/")
        if (sanitized.isBlank()) return null

        val parsed = sanitized.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null

        return sanitized
    }
}

data class RivestreamSettingsState(
    val apiEndpoint: String,
    val defaultQuality: String,
    val language: String,
    val cacheEnabled: Boolean,
    val cacheMinutes: Int,
)

data class SettingsUiModel(
    val title: String,
    val endpointLabel: String,
    val endpointHint: String,
    val endpointValue: String,
    val qualityLabel: String,
    val qualityOptions: List<String>,
    val qualityValue: String,
    val languageLabel: String,
    val languageHint: String,
    val languageValue: String,
    val cacheLabel: String,
    val cacheEnabled: Boolean,
    val cacheDurationLabel: String,
    val cacheDurationMinutes: Int,
    val validationError: String?,
)
