package com.rivestream

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.rivestream.models.ContentMetadata
import com.rivestream.models.ContentType
import com.rivestream.models.StreamLink
import com.rivestream.models.StreamResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RivestreamAPI(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {
    companion object {
        const val DEFAULT_ENDPOINT = "https://api.rivestream.org"
    }

    fun normalizeEndpoint(endpoint: String?): String? {
        val sanitized = endpoint?.trim().orEmpty().removeSuffix("/")
        if (sanitized.isBlank()) return null
        val parsed = sanitized.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        return sanitized
    }

    fun search(endpoint: String, query: String, page: Int = 1): List<ContentMetadata> {
        val normalizedEndpoint = normalizeEndpoint(endpoint) ?: return emptyList()
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || page < 1) return emptyList()

        val encodedQuery = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
        val url = "$normalizedEndpoint/search?query=$encodedQuery&page=$page"

        return try {
            val body = get(url) ?: return emptyList()
            val response = gson.fromJson(body, SearchApiResponse::class.java) ?: return emptyList()
            response.results.orEmpty().mapNotNull { item ->
                val type = when (item.type?.lowercase()) {
                    "movie" -> ContentType.MOVIE
                    "tv", "tvshow", "show" -> ContentType.TVSHOW
                    else -> null
                } ?: return@mapNotNull null

                val tmdbId = item.tmdbId ?: return@mapNotNull null
                if (tmdbId <= 0) return@mapNotNull null

                ContentMetadata(
                    tmdbId = tmdbId,
                    title = item.title.orEmpty().ifBlank { "Unknown title" },
                    type = type,
                    year = item.year,
                )
            }
        } catch (e: Exception) {
            println("[RivestreamAPI] Search failed: ${e.message}")
            emptyList()
        }
    }

    fun getStreams(endpoint: String, content: ContentMetadata): StreamResponse {
        val normalizedEndpoint = normalizeEndpoint(endpoint)
            ?: return StreamResponse(success = false, message = "Invalid Rivestream endpoint")

        if (content.tmdbId <= 0) {
            return StreamResponse(success = false, message = "TmdbId is required")
        }

        if (content.type == ContentType.TVSHOW && (content.season == null || content.episode == null)) {
            return StreamResponse(success = false, message = "Season and episode are required for TV content")
        }

        val typeParam = if (content.type == ContentType.MOVIE) "movie" else "tv"
        val url = buildString {
            append("$normalizedEndpoint/stream?tmdbId=${content.tmdbId}&type=$typeParam")
            if (content.type == ContentType.TVSHOW) {
                append("&season=${content.season}&episode=${content.episode}")
            }
        }

        return try {
            val body = get(url) ?: return StreamResponse(success = false, message = "Empty API response")
            val response = gson.fromJson(body, StreamApiResponse::class.java)
                ?: return StreamResponse(success = false, message = "Malformed API response")

            val links = response.links.orEmpty().mapNotNull { link ->
                val urlValue = link.url?.trim().orEmpty()
                if (urlValue.isBlank()) return@mapNotNull null
                StreamLink(
                    quality = link.quality.orEmpty().ifBlank { "Auto" },
                    language = link.language.orEmpty().ifBlank { "en" },
                    url = urlValue,
                    isDownload = link.isDownload ?: false,
                )
            }

            if (links.isEmpty()) {
                StreamResponse(success = false, message = response.message ?: "No stream links found")
            } else {
                StreamResponse(success = true, links = links, message = response.message)
            }
        } catch (e: Exception) {
            println("[RivestreamAPI] Stream fetch failed: ${e.message}")
            StreamResponse(success = false, message = "Failed to fetch stream links")
        }
    }

    fun getEpisodes(endpoint: String, tmdbId: Int, season: Int): List<ContentMetadata> {
        val normalizedEndpoint = normalizeEndpoint(endpoint) ?: return emptyList()
        if (tmdbId <= 0 || season <= 0) return emptyList()

        val url = "$normalizedEndpoint/episodes?tmdbId=$tmdbId&season=$season"

        return try {
            val body = get(url) ?: return emptyList()
            val response = gson.fromJson(body, EpisodesApiResponse::class.java) ?: return emptyList()

            response.episodes.orEmpty().mapNotNull { item ->
                val episodeNumber = item.episode ?: return@mapNotNull null
                ContentMetadata(
                    tmdbId = tmdbId,
                    title = item.title.orEmpty().ifBlank { "Episode $episodeNumber" },
                    type = ContentType.TVSHOW,
                    season = season,
                    episode = episodeNumber,
                )
            }
        } catch (e: Exception) {
            println("[RivestreamAPI] Episode fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("[RivestreamAPI] HTTP ${response.code} for $url")
                    return null
                }
                response.body?.string()
            }
        } catch (ioe: IOException) {
            println("[RivestreamAPI] Network failure: ${ioe.message}")
            null
        } catch (jse: JsonSyntaxException) {
            println("[RivestreamAPI] JSON parsing failure: ${jse.message}")
            null
        }
    }

    private data class SearchApiResponse(val results: List<SearchItem>?)

    private data class SearchItem(
        val tmdbId: Int?,
        val title: String?,
        val type: String?,
        val year: Int?,
    )

    private data class StreamApiResponse(
        val links: List<StreamApiLink>?,
        val message: String?,
    )

    private data class StreamApiLink(
        val quality: String?,
        val language: String?,
        val url: String?,
        val isDownload: Boolean?,
    )

    private data class EpisodesApiResponse(val episodes: List<EpisodeApiItem>?)

    private data class EpisodeApiItem(
        val episode: Int?,
        val title: String?,
    )
}
