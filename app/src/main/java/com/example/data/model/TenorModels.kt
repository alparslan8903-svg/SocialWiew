package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TenorSearchResponse(
    val results: List<TenorResult> = emptyList(),
    val next: String = ""
)

@JsonClass(generateAdapter = true)
data class TenorResult(
    val id: String = "",
    val title: String = "",
    @Json(name = "content_description") val contentDescription: String = "",
    @Json(name = "media_formats") val mediaFormats: TenorMediaFormats = TenorMediaFormats()
)

@JsonClass(generateAdapter = true)
data class TenorMediaFormats(
    val gif: TenorMedia? = null,
    val tinygif: TenorMedia? = null,
    val nanogif: TenorMedia? = null
)

@JsonClass(generateAdapter = true)
data class TenorMedia(
    val url: String = "",
    val dims: List<Int> = emptyList()
)

data class TenorGifItem(
    val id: String,
    val title: String,
    val fullUrl: String,
    val previewUrl: String
)
