package com.rivestream

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rivestream.models.ContentType
import com.rivestream.models.SearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TMDBClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val errorHandler: ErrorHandler = ErrorHandler(),
    private val apiKeyProvider: () -> String = { System.getenv("TMDB_API_KEY").orEmpty() },
    private val baseUrl: String = "https://api.themoviedb.org/3",
) {
    fun isConfigured(): Boolean = apiKeyProvider().isNotBlank()

    fun search(query: String, page: Int = 1, language: String = "en-US"): List<SearchResult> {
        if (query.isBlank() || page < 1 || !isConfigured()) return emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val encodedLanguage = URLEncoder.encode(language, StandardCharsets.UTF_8.name())
        val url =
            "$baseUrl/search/multi?api_key=${apiKeyProvider()}&query=$encodedQuery&page=$page&language=$encodedLanguage"

        return try {
            errorHandler.executeWithRetry {
                val body = request(url) ?: return@executeWithRetry emptyList()
                val response = gson.fromJson(body, TmdbSearchResponse::class.java) ?: return@executeWithRetry emptyList()

                response.results.orEmpty().mapNotNull { item ->
                    val id = item.id ?: return@mapNotNull null
                    if (id <= 0) return@mapNotNull null

                    val type =
                        when (item.mediaType?.lowercase()) {
                            "movie" -> ContentType.MOVIE
                            "tv" -> ContentType.TVSHOW
                            else -> null
                        } ?: return@mapNotNull null

                    val title = item.title?.takeIf { it.isNotBlank() }
                        ?: item.name?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                    SearchResult(
                        tmdbId = id,
                        title = title,
                        type = type,
                        year = extractYear(type, item.releaseDate, item.firstAirDate),
                        language = item.originalLanguage ?: "en",
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun resolveTmdbId(
        title: String,
        type: ContentType,
        year: Int? = null,
        language: String = "en-US",
    ): Int? {
        if (title.isBlank()) return null

        val results = search(query = title, page = 1, language = language)
            .filter { it.type == type }

        return results.firstOrNull { candidate ->
            year == null || candidate.year == null || candidate.year == year
        }?.tmdbId
    }

    private fun request(url: String): String? {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 429) {
                    throw RateLimitException(response.header("Retry-After")?.toIntOrNull())
                }
                return null
            }
            response.body?.string()
        }
    }

    private fun extractYear(type: ContentType, movieDate: String?, tvDate: String?): Int? {
        val source = if (type == ContentType.MOVIE) movieDate else tvDate
        return source?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    }

    private data class TmdbSearchResponse(
        val results: List<TmdbResult>?,
    )

    private data class TmdbResult(
        val id: Int?,
        @SerializedName("media_type")
        val mediaType: String?,
        val title: String?,
        val name: String?,
        @SerializedName("release_date")
        val releaseDate: String?,
        @SerializedName("first_air_date")
        val firstAirDate: String?,
        @SerializedName("original_language")
        val originalLanguage: String?,
    )
}
