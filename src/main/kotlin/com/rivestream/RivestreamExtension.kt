package com.rivestream

import com.rivestream.models.ContentMetadata
import com.rivestream.models.ContentType
import com.rivestream.models.StreamLink

class RivestreamExtension(
    private val api: RivestreamAPI = RivestreamAPI(),
    private val settings: RivestreamSettings = RivestreamSettings(),
    private val tmdbClient: TMDBClient = TMDBClient(),
    private val errorHandler: ErrorHandler = ErrorHandler(),
) {
    private var lastErrorMessage: String? = null

    fun getName(): String = "Rivestream"

    fun getDescription(): String =
        "CloudStream 4.x extension for Rivestream API with movie/TV search, " +
            "stream and download links, episode support, configurable endpoints, and TMDB integration."

    fun getBaseUrl(): String = settings.getState().apiEndpoint

    fun getContentTypes(): Set<ContentType> = setOf(ContentType.MOVIE, ContentType.TVSHOW)

    fun getTranslationLanguages(): Set<String> = setOf(settings.getState().language)

    fun getLastErrorMessage(): String? = lastErrorMessage

    /**
     * TMDB metadata is preferred when duplicate TMDB IDs are found, with Rivestream data used as fallback.
     */
    fun search(query: String, page: Int): List<ContentMetadata> {
        val state = settings.getState()
        val rivestreamResults =
            try {
                api.search(endpoint = state.apiEndpoint, query = query, page = page)
            } catch (error: Throwable) {
                lastErrorMessage = errorHandler.toUserMessage(error)
                emptyList()
            }

        val tmdbResults = tmdbClient.search(query = query, page = page, language = state.language)
            .map { result ->
                ContentMetadata(
                    tmdbId = result.tmdbId,
                    title = result.title,
                    type = result.type,
                    year = result.year,
                )
            }

        val merged = linkedMapOf<Int, ContentMetadata>()
        tmdbResults.filter { it.tmdbId > 0 }.forEach { merged[it.tmdbId] = it }
        rivestreamResults.filter { it.tmdbId > 0 }.forEach { merged.putIfAbsent(it.tmdbId, it) }

        return merged.values.toList()
    }

    fun getStreamUrls(content: Content): List<StreamLink> {
        val state = settings.getState()
        val resolvedTmdbId = content.tmdbId.takeIf { it > 0 }
            ?: tmdbClient.resolveTmdbId(
                title = content.title,
                type = content.type,
                year = content.year,
                language = state.language,
            )

        if (resolvedTmdbId == null || resolvedTmdbId <= 0) {
            lastErrorMessage = RivestreamError.MissingTmdbId.userMessage
            return emptyList()
        }

        val resolvedContent = content.copy(tmdbId = resolvedTmdbId).toMetadata()

        return try {
            val response = api.getStreams(state.apiEndpoint, resolvedContent)
            if (!response.success) {
                lastErrorMessage = response.message ?: "Unable to fetch stream links"
                emptyList()
            } else {
                lastErrorMessage = null
                response.links
            }
        } catch (error: Throwable) {
            lastErrorMessage = errorHandler.toUserMessage(error)
            emptyList()
        }
    }

    fun getEpisodes(seasonContent: SeasonContent): List<ContentMetadata> {
        val state = settings.getState()
        return try {
            api.getEpisodes(
                endpoint = state.apiEndpoint,
                tmdbId = seasonContent.tmdbId,
                season = seasonContent.season,
            )
        } catch (error: Throwable) {
            lastErrorMessage = errorHandler.toUserMessage(error)
            emptyList()
        }
    }

    fun getSettingsUi(): SettingsUiModel = settings.getSettingsUi()

    fun updateEndpoint(endpointInput: String): SettingsUiModel = settings.updateApiEndpoint(endpointInput)

    fun updateDefaultQuality(quality: String): SettingsUiModel = settings.updateDefaultQuality(quality)

    fun updateLanguage(language: String): SettingsUiModel = settings.updateLanguage(language)

    fun updateCacheEnabled(enabled: Boolean): SettingsUiModel = settings.updateCacheEnabled(enabled)

    fun updateCacheMinutes(minutes: Int): SettingsUiModel = settings.updateCacheMinutes(minutes)
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
