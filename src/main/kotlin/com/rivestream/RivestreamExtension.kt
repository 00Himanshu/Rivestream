package com.rivestream

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.rivestream.models.ContentMetadata
import com.rivestream.models.ContentType
import com.rivestream.models.StreamLink

class RivestreamExtension(
    private val api: RivestreamAPI = RivestreamAPI(),
    private val settings: RivestreamSettings = RivestreamSettings(),
    private val tmdbClient: TMDBClient = TMDBClient(),
    private val errorHandler: ErrorHandler = ErrorHandler(),
    private val gson: Gson = Gson(),
) : MainAPI() {
    override var name = "Rivestream"
    override var mainUrl = RivestreamAPI.DEFAULT_ENDPOINT
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        mainPage("popular", "Popular"),
        mainPage("trending", "Trending"),
    )

    private var lastErrorMessage: String? = null

    fun getName(): String = name

    fun getDescription(): String =
        "CloudStream 4.x extension for Rivestream API with movie/TV search, " +
            "stream and download links, episode support, configurable endpoints, and TMDB integration."

    fun getBaseUrl(): String = currentState().apiEndpoint

    fun getContentTypes(): Set<ContentType> = setOf(ContentType.MOVIE, ContentType.TVSHOW)

    fun getTranslationLanguages(): Set<String> = setOf(currentState().language)

    fun getLastErrorMessage(): String? = lastErrorMessage

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val state = currentState()
        val query = request.data.ifBlank { request.name }.ifBlank { "popular" }
        val items = searchContent(state = state, query = query, page = page).mapNotNull(::toSearchResponse)
        return newHomePageResponse(request, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val state = currentState()
        val items = searchContent(state = state, query = query, page = page).mapNotNull(::toSearchResponse)
        return newSearchResponseList(items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse>? = search(query = query, page = 1)?.items

    override suspend fun load(url: String): LoadResponse? = getUrl(url)

    suspend fun getUrl(url: String): LoadResponse? {
        val state = currentState()
        val data = parseLoadData(url) ?: return null

        return try {
            when (data.type) {
                ContentType.MOVIE ->
                    newMovieLoadResponse(
                        name = data.title,
                        url = url,
                        type = TvType.Movie,
                        dataUrl = toDataUrl(data),
                    )

                ContentType.TVSHOW -> {
                    val episodes =
                        if (data.season != null && data.episode != null) {
                            listOf(toEpisode(data))
                        } else {
                            val fallbackSeason = data.season ?: 1
                            val episodeMetadata = api.getEpisodes(
                                endpoint = state.apiEndpoint,
                                tmdbId = data.tmdbId,
                                season = fallbackSeason,
                            )
                            episodeMetadata.map {
                                toEpisode(
                                    data.copy(
                                        season = it.season ?: fallbackSeason,
                                        episode = it.episode,
                                        title = it.title.ifBlank { data.title },
                                    ),
                                )
                            }
                        }

                    newTvSeriesLoadResponse(
                        name = data.title,
                        url = url,
                        type = TvType.TvSeries,
                        episodes = episodes,
                    )
                }
            }
        } catch (error: Throwable) {
            lastErrorMessage = errorHandler.toUserMessage(error)
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = extractVideo(data, isCasting, subtitleCallback, callback)

    suspend fun extractVideo(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val state = currentState()
        val parsedData = parseLoadData(data) ?: return false
        val links =
            getStreamUrls(
                Content(
                    tmdbId = parsedData.tmdbId,
                    title = parsedData.title,
                    type = parsedData.type,
                    season = parsedData.season,
                    episode = parsedData.episode,
                    year = parsedData.year,
                ),
            )

        links.forEach { link ->
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "${name} ${link.quality}".trim(),
                    url = link.url,
                    type = if (link.url.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                ) {
                    referer = state.apiEndpoint
                    quality = getQualityFromString(link.quality)
                    headers = mapOf("Accept-Language" to state.language)
                },
            )
        }

        return links.isNotEmpty()
    }

    /**
     * TMDB metadata is preferred when duplicate TMDB IDs are found, with Rivestream data used as fallback.
     */
    fun searchContent(query: String, page: Int): List<ContentMetadata> {
        val state = currentState()
        return searchContent(state = state, query = query, page = page)
    }

    fun getStreamUrls(content: Content): List<StreamLink> {
        val state = currentState()
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
        val state = currentState()
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

    private fun currentState(): RivestreamSettingsState {
        val state = settings.getState()
        mainUrl = state.apiEndpoint
        lang = state.language
        return state
    }

    private fun searchContent(state: RivestreamSettingsState, query: String, page: Int): List<ContentMetadata> {
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

    private fun toSearchResponse(content: ContentMetadata): SearchResponse? {
        val loadData = LoadData(
            tmdbId = content.tmdbId,
            title = content.title,
            type = content.type,
            season = content.season,
            episode = content.episode,
            year = content.year,
        )

        val url = toDataUrl(loadData)
        return when (content.type) {
            ContentType.MOVIE ->
                newMovieSearchResponse(content.title, url, TvType.Movie, fix = false) {
                    this.year = content.year
                }

            ContentType.TVSHOW ->
                newTvSeriesSearchResponse(content.title, url, TvType.TvSeries, fix = false) {
                    this.year = content.year
                }
        }
    }

    private fun toDataUrl(data: LoadData): String = gson.toJson(data)

    private fun parseLoadData(value: String): LoadData? =
        try {
            gson.fromJson(value, LoadData::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }

    private fun toEpisode(data: LoadData): Episode =
        newEpisode(toDataUrl(data), fix = false) {
            name = data.title
            season = data.season
            episode = data.episode
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

private data class LoadData(
    val tmdbId: Int,
    val title: String,
    val type: ContentType,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
)
