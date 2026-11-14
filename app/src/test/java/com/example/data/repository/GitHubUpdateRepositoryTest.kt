package com.example.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateRepositoryTest {

    @Test
    fun parseGitHubRepoSlug_handlesUrlsAndSlugs() {
        assertEquals("alparslan8903/SocialWiew", GitHubUpdateRepository.parseGitHubRepoSlug("alparslan8903/SocialWiew"))
        assertEquals(
            "alparslan8903/SocialWiew",
            GitHubUpdateRepository.parseGitHubRepoSlug("https://github.com/alparslan8903/SocialWiew")
        )
        assertEquals(
            "alparslan8903/SocialWiew",
            GitHubUpdateRepository.parseGitHubRepoSlug("https://github.com/alparslan8903/SocialWiew.git")
        )
        assertEquals(
            "alparslan8903/SocialWiew",
            GitHubUpdateRepository.parseGitHubRepoSlug("https://github.com/alparslan8903/SocialWiew/releases/latest")
        )
        assertNull(GitHubUpdateRepository.parseGitHubRepoSlug("https://example.com/foo/bar"))
        assertNull(GitHubUpdateRepository.parseGitHubRepoSlug("onlyowner"))
        assertNull(GitHubUpdateRepository.parseGitHubRepoSlug(""))
    }

    @Test
    fun isNewerVersion_comparesSemverCorrectly() {
        assertTrue(GitHubUpdateRepository.isNewerVersion("v1.1.0", "1.0"))
        assertTrue(GitHubUpdateRepository.isNewerVersion("v1.0.1", "1.0"))
        assertTrue(GitHubUpdateRepository.isNewerVersion("2.0", "1.9.9"))
        assertFalse(GitHubUpdateRepository.isNewerVersion("v1.0.0", "1.0"))
        assertFalse(GitHubUpdateRepository.isNewerVersion("v0.9.5", "1.0"))
    }

    @Test
    fun checkForUpdate_parsesReleaseNotesAndApkAsset() = runBlocking {
        val releaseJson = """
            {
              "tag_name": "v1.2.0",
              "name": "SocialWiew 1.2.0 - Yeni Özellikler",
              "body": "- Mavi tik desteği\n- GIF görünümü iyileştirildi",
              "html_url": "https://github.com/alparslan8903/SocialWiew/releases/tag/v1.2.0",
              "published_at": "2026-11-14T08:00:00Z",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "SocialWiew-v1.2.0.apk",
                  "browser_download_url": "https://github.com/alparslan8903/SocialWiew/releases/download/v1.2.0/SocialWiew-v1.2.0.apk",
                  "size": 29000000
                }
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(releaseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val repo = GitHubUpdateRepository(httpClient = client)
        val info = repo.checkForUpdate("alparslan8903/SocialWiew", currentVersion = "1.0").getOrThrow()

        assertTrue(info.isNewerAvailable)
        assertEquals("v1.2.0", info.tagName)
        assertEquals("SocialWiew 1.2.0 - Yeni Özellikler", info.title)
        assertTrue(info.releaseNotes.contains("Mavi tik"))
        assertEquals(
            "https://github.com/alparslan8903/SocialWiew/releases/download/v1.2.0/SocialWiew-v1.2.0.apk",
            info.apkDownloadUrl
        )
    }

    @Test
    fun checkForUpdate_fallsBackToTagsWhenNoReleaseExists() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                if (path.endsWith("/tags")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""[{"name":"v1.3.0"}]""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("""{"message":"Not Found"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val repo = GitHubUpdateRepository(httpClient = client)
        val info = repo.checkForUpdate("https://github.com/alparslan8903/SocialWiew", currentVersion = "1.0").getOrThrow()

        assertTrue(info.isNewerAvailable)
        assertEquals("v1.3.0", info.tagName)
        assertEquals("https://github.com/alparslan8903/SocialWiew/releases/tag/v1.3.0", info.htmlUrl)
        assertNull(info.apkDownloadUrl)
    }
}
