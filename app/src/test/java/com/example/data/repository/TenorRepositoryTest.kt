package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class TenorRepositoryTest {

    private val repo = TenorRepository()

    @Test
    fun buildTenorUrl_handlesBlankAndTurkishQueriesWithoutApiKey() {
        assertEquals("https://tenor.com/", repo.buildTenorUrl(""))
        assertEquals("https://tenor.com/", repo.buildTenorUrl("   "))
        assertEquals("https://tenor.com/search/alkis-gifs", repo.buildTenorUrl("Alkış"))
        assertEquals("https://tenor.com/search/happy-dance-gifs", repo.buildTenorUrl("Happy Dance!"))
    }

    @Test
    fun parseGifsFromHtml_deduplicatesVariantsAndPicksPreviewAndFullUrls() {
        val sampleHtml = """
            <img src="https://media.tenor.com/0eLcXZRPxCAAAAAM/happy-dance-cats.gif" />
            <img src="https://media1.tenor.com/m/0eLcXZRPxCAAAAAC/happy-dance-cats.gif" />
            <img src="https://media.tenor.com/HCyNMWQv868AAAAM/good-night.gif" />
        """.trimIndent()

        val gifs = repo.parseGifsFromHtml(sampleHtml)
        assertEquals(2, gifs.size)
        assertEquals("0eLcXZRPxC", gifs[0].id)
        assertEquals("https://media.tenor.com/0eLcXZRPxCAAAAAM/happy-dance-cats.gif", gifs[0].previewUrl)
        assertEquals("https://media1.tenor.com/m/0eLcXZRPxCAAAAAC/happy-dance-cats.gif", gifs[0].fullUrl)
        assertTrue(gifs[0].title.contains("happy dance cats"))
    }
}
