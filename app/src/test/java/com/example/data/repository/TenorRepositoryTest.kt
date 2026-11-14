package com.example.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class TenorRepositoryTest {

    @Test
    fun placeholderOrBlankKey_returnsMissingApiKeyState() = runBlocking {
        val placeholderRepo = TenorRepository(apiKey = "MY_TENOR_API_KEY")
        assertFalse(placeholderRepo.isConfigured())
        assertTrue(placeholderRepo.loadGifs("") is TenorUiState.MissingApiKey)

        val blankRepo = TenorRepository(apiKey = "")
        assertFalse(blankRepo.isConfigured())
        assertTrue(blankRepo.loadGifs("dance") is TenorUiState.MissingApiKey)
    }

    @Test
    fun nonPlaceholderKey_reportsConfigured() {
        val configuredRepo = TenorRepository(apiKey = "test-tenor-key-123")
        assertTrue(configuredRepo.isConfigured())
    }
}
