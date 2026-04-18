package com.rivestream

import com.rivestream.models.ContentType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TMDBClientTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search maps movie and tv results`() {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[
                  {"id":157336,"media_type":"movie","title":"Interstellar","release_date":"2014-11-05","original_language":"en"},
                  {"id":1399,"media_type":"tv","name":"Game of Thrones","first_air_date":"2011-04-17","original_language":"en"}
                ]}
                """.trimIndent(),
            ),
        )

        val client = TMDBClient(
            apiKeyProvider = { "tmdb-key" },
            baseUrl = server.url("/3").toString().removeSuffix("/"),
        )

        val results = client.search("test", 1, "en-us")

        assertEquals(2, results.size)
        assertEquals(157336, results[0].tmdbId)
        assertEquals(ContentType.MOVIE, results[0].type)
        assertEquals(1399, results[1].tmdbId)
        assertEquals(ContentType.TVSHOW, results[1].type)
    }

    @Test
    fun `resolveTmdbId returns null when key missing`() {
        val client = TMDBClient(apiKeyProvider = { "" })

        val id = client.resolveTmdbId("Interstellar", ContentType.MOVIE, 2014)

        assertNull(id)
    }
}
