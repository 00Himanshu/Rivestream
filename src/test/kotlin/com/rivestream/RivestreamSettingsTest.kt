package com.rivestream

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.prefs.Preferences

class RivestreamSettingsTest {
    private val prefs = Preferences.userRoot().node("rivestream-test-settings")
    private val settings = RivestreamSettings(prefs)

    @AfterEach
    fun cleanup() {
        prefs.clear()
    }

    @Test
    fun `invalid endpoint returns validation error`() {
        val ui = settings.updateApiEndpoint("not-a-url")

        assertNotNull(ui.validationError)
        assertEquals(RivestreamAPI.DEFAULT_ENDPOINT, ui.endpointValue)
    }

    @Test
    fun `settings updates persist to state`() {
        settings.updateApiEndpoint("https://example.com")
        settings.updateDefaultQuality("720p")
        settings.updateLanguage("en-us")
        settings.updateCacheEnabled(false)
        settings.updateCacheMinutes(10)

        val state = settings.getState()
        assertEquals("https://example.com", state.apiEndpoint)
        assertEquals("720p", state.defaultQuality)
        assertEquals("en-us", state.language)
        assertEquals(false, state.cacheEnabled)
        assertEquals(10, state.cacheMinutes)
    }

    @Test
    fun `valid endpoint clears validation errors`() {
        val ui = settings.updateApiEndpoint("https://api.rivestream.org")

        assertNull(ui.validationError)
        assertEquals("https://api.rivestream.org", ui.endpointValue)
    }
}
