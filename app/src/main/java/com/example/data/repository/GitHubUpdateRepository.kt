package com.example.data.repository

import android.util.Log
import com.example.data.model.AppUpdateInfo
import com.example.data.model.GitHubReleaseDto
import com.example.data.model.GitHubTagDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubUpdateRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
) {
    private val releaseAdapter by lazy { moshi.adapter(GitHubReleaseDto::class.java) }
    private val releaseListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, GitHubReleaseDto::class.java)
        moshi.adapter<List<GitHubReleaseDto>>(type)
    }
    private val tagListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, GitHubTagDto::class.java)
        moshi.adapter<List<GitHubTagDto>>(type)
    }

    suspend fun checkForUpdate(
        rawRepoInput: String,
        currentVersion: String
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = parseGitHubRepoSlug(rawRepoInput)
                ?: throw IllegalArgumentException("Geçerli bir GitHub deposu girin (ör. kullanici/SocialWiew).")

            val latestRelease = fetchLatestRelease(slug)
                ?: fetchFirstListedRelease(slug)

            if (latestRelease != null) {
                val tag = latestRelease.tagName.ifBlank { latestRelease.name.orEmpty() }.ifBlank { "1.0" }
                val apkAssetUrl = latestRelease.assets
                    .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank() }
                    ?.browserDownloadUrl
                val notes = latestRelease.body?.trim().orEmpty()
                    .ifBlank { "Yeni sürüm GitHub üzerinde yayınlandı." }
                val title = latestRelease.name?.trim().orEmpty()
                    .ifBlank { "SocialWiew $tag" }
                val pageUrl = latestRelease.htmlUrl.ifBlank { "https://github.com/$slug/releases" }

                return@runCatching AppUpdateInfo(
                    repoSlug = slug,
                    tagName = tag,
                    title = title,
                    releaseNotes = notes,
                    htmlUrl = pageUrl,
                    apkDownloadUrl = apkAssetUrl,
                    publishedAt = latestRelease.publishedAt.orEmpty(),
                    currentVersion = currentVersion,
                    isNewerAvailable = isNewerVersion(tag, currentVersion)
                )
            }

            val latestTag = fetchFirstTag(slug)
                ?: throw IllegalStateException("GitHub deposunda henüz yayınlanmış bir sürüm (Release/Tag) bulunamadı.")

            val tagName = latestTag.name.ifBlank { "1.0" }
            AppUpdateInfo(
                repoSlug = slug,
                tagName = tagName,
                title = "SocialWiew $tagName",
                releaseNotes = "GitHub'da $tagName etiketi yayınlandı. Değişiklikleri GitHub üzerinden inceleyebilirsiniz.",
                htmlUrl = "https://github.com/$slug/releases/tag/$tagName",
                apkDownloadUrl = null,
                publishedAt = "",
                currentVersion = currentVersion,
                isNewerAvailable = isNewerVersion(tagName, currentVersion)
            )
        }
    }

    private fun fetchLatestRelease(repoSlug: String): GitHubReleaseDto? {
        val url = "https://api.github.com/repos/$repoSlug/releases/latest"
        val responseBody = executeGet(url, allow404 = true) ?: return null
        return runCatching { releaseAdapter.fromJson(responseBody) }.getOrNull()
            ?.takeIf { !it.draft && it.tagName.isNotBlank() }
    }

    private fun fetchFirstListedRelease(repoSlug: String): GitHubReleaseDto? {
        val url = "https://api.github.com/repos/$repoSlug/releases?per_page=3"
        val responseBody = executeGet(url, allow404 = true) ?: return null
        val list = runCatching { releaseListAdapter.fromJson(responseBody) }.getOrNull().orEmpty()
        return list.firstOrNull { !it.draft && it.tagName.isNotBlank() }
    }

    private fun fetchFirstTag(repoSlug: String): GitHubTagDto? {
        val url = "https://api.github.com/repos/$repoSlug/tags?per_page=3"
        val responseBody = executeGet(url, allow404 = false) ?: return null
        val list = runCatching { tagListAdapter.fromJson(responseBody) }.getOrNull().orEmpty()
        return list.firstOrNull { it.name.isNotBlank() }
    }

    private fun executeGet(url: String, allow404: Boolean): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SocialWiew-Android")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404 && allow404) return null
            if (response.code == 404) {
                throw IllegalStateException("GitHub deposu bulunamadı veya herkese açık değil.")
            }
            if (response.code == 403 || response.code == 429) {
                throw IllegalStateException("GitHub API istek sınırına ulaşıldı, lütfen biraz sonra tekrar deneyin.")
            }
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API HTTP ${response.code} for $url")
                throw IllegalStateException("GitHub güncellemeleri alınamadı (HTTP ${response.code}).")
            }
            return response.body?.string()
        }
    }

    companion object {
        private const val TAG = "GitHubUpdateRepo"
        private val SEGMENT_REGEX = Regex("^[A-Za-z0-9_.-]+$")

        fun parseGitHubRepoSlug(rawInput: String): String? {
            val trimmed = rawInput.trim()
                .removeSuffix("/")
                .removeSuffix(".git")
            if (trimmed.isBlank()) return null

            val pathPart = when {
                trimmed.startsWith("https://github.com/", ignoreCase = true) ->
                    trimmed.substringAfter("github.com/", "")
                trimmed.startsWith("http://github.com/", ignoreCase = true) ->
                    trimmed.substringAfter("github.com/", "")
                trimmed.startsWith("github.com/", ignoreCase = true) ->
                    trimmed.substringAfter("github.com/", "")
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> return null
                else -> trimmed
            }

            val segments = pathPart.split('/').filter { it.isNotBlank() }
            if (segments.size < 2) return null
            val owner = segments[0]
            val repo = segments[1]
            if (!SEGMENT_REGEX.matches(owner) || !SEGMENT_REGEX.matches(repo)) return null
            return "$owner/$repo"
        }

        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val remoteNums = extractNumericParts(remoteTag)
            val currentNums = extractNumericParts(currentVersion)
            if (remoteNums.isEmpty()) return false
            if (currentNums.isEmpty()) return true

            val maxLen = maxOf(remoteNums.size, currentNums.size)
            for (i in 0 until maxLen) {
                val r = remoteNums.getOrElse(i) { 0 }
                val c = currentNums.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            // Same numeric version (e.g. 1.0 vs 1.0-rc1 -> pre-release is not newer)
            val remoteHasPre = remoteTag.trim().contains("-")
            val currentHasPre = currentVersion.trim().contains("-")
            return !remoteHasPre && currentHasPre
        }

        private fun extractNumericParts(version: String): List<Int> {
            val core = version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (core.isBlank()) return emptyList()
            return core.split('.')
                .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
        }
    }
}
