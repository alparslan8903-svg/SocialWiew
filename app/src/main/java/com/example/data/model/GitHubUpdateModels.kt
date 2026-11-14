package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubTagDto(
    val name: String = ""
)

data class AppUpdateInfo(
    val repoSlug: String,
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String,
    val currentVersion: String,
    val isNewerAvailable: Boolean
)
