package com.rivestream

import com.rivestream.models.ContentMetadata
import com.rivestream.models.ContentType
import com.rivestream.models.StreamLink

class RivestreamExtension(
    private val api: RivestreamAPI = RivestreamAPI(),
) {
    private var configuredEndpoint: String = RivestreamAPI.DEFAULT_ENDPOINT

    fun getName(): String = "Rivestream"

    fun getDescription(): String =
        "CloudStream 4.x extension for Rivestream API with movie/TV search, " +
            "stream and download links, episode support, and configurable endpoints."

    fun getBaseUrl(): String = configuredEndpoint

    fun getContentTypes(): Set<ContentType> = setOf(ContentType.MOVIE, ContentType.TVSHOW)

    fun getTranslationLanguages(): Set<String> = setOf("en")

    fun search(query: String, page: Int): List<ContentMetadata> =
        api.search(endpoint = getBaseUrl(), query = query, page = page)

    fun getStreamUrls(content: Content): List<StreamLink> {
        val response = api.getStreams(getBaseUrl(), content.toMetadata())
        return response.links
    }

    fun getEpisodes(seasonContent: SeasonContent): List<ContentMetadata> =
        api.getEpisodes(
            endpoint = getBaseUrl(),
            tmdbId = seasonContent.tmdbId,
            season = seasonContent.season,
        )

    fun getSettingsUi(): SettingsUiModel =
        SettingsUiModel(
            title = "Rivestream Settings",
            endpointLabel = "API Endpoint",
            endpointHint = RivestreamAPI.DEFAULT_ENDPOINT,
            endpointValue = configuredEndpoint,
            validationError = null,
        )

    fun updateEndpoint(endpointInput: String): SettingsUiModel {
        val normalized = api.normalizeEndpoint(endpointInput)
        return if (normalized != null) {
            configuredEndpoint = normalized
            getSettingsUi()
        } else {
            getSettingsUi().copy(validationError = "Please enter a valid http/https endpoint URL")
        }
    }
}

data class Content(
    val tmdbId: Int,
    val title: String,
    val type: ContentType,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
) {
    fun toMetadata(): ContentMetadata =
        ContentMetadata(
            tmdbId = tmdbId,
            title = title,
            type = type,
            season = season,
            episode = episode,
            year = year,
        )
}

data class SeasonContent(
    val tmdbId: Int,
    val season: Int,
)

data class SettingsUiModel(
    val title: String,
    val endpointLabel: String,
    val endpointHint: String,
    val endpointValue: String,
    val validationError: String?,
)
