package com.example.data.repository

import android.util.Log
import com.example.data.model.TenorGifItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed interface TenorUiState {
    data object Idle : TenorUiState
    data object Loading : TenorUiState
    data class Success(val gifs: List<TenorGifItem>) : TenorUiState
    data class Error(val message: String) : TenorUiState
}

class TenorRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun loadGifs(query: String): TenorUiState = withContext(Dispatchers.IO) {
        try {
            val targetUrl = buildTenorUrl(query)
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TenorUiState.Error("Tenor GIF'leri yüklenemedi (HTTP ${response.code}).")
                }
                val html = response.body?.string().orEmpty()
                val gifs = parseGifsFromHtml(html)
                TenorUiState.Success(gifs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tenor keyless load error", e)
            TenorUiState.Error(e.localizedMessage ?: "Tenor GIF'leri yüklenemedi.")
        }
    }

    internal fun buildTenorUrl(query: String): String {
        val clean = query.trim()
        if (clean.isEmpty()) return "https://tenor.com/"
        val folded = foldTurkishChars(clean.lowercase())
            .replace(Regex("[^a-z0-9\\s-]+"), " ")
            .trim()
            .replace(Regex("\\s+"), "-")
        val slug = if (folded.isNotEmpty()) folded else URLEncoder.encode(clean, "UTF-8")
        return "https://tenor.com/search/${slug}-gifs"
    }

    internal fun parseGifsFromHtml(html: String, limit: Int = 24): List<TenorGifItem> {
        if (html.isBlank()) return emptyList()

        val variantsByAsset = linkedMapOf<String, MutableList<Pair<String, String>>>()
        for (match in TENOR_GIF_REGEX.findAll(html)) {
            val fullMatchedUrl = match.value
            val rawAssetId = match.groupValues[1]
            val slugName = match.groupValues[2]
            val canonicalId = if (rawAssetId.length > 6 && rawAssetId.substring(rawAssetId.length - 6).startsWith("AAAA")) {
                rawAssetId.dropLast(6)
            } else {
                rawAssetId
            }
            val list = variantsByAsset.getOrPut(canonicalId) { mutableListOf() }
            if (list.none { it.first == fullMatchedUrl }) {
                list.add(fullMatchedUrl to slugName)
            }
        }

        return variantsByAsset.entries
            .take(limit)
            .map { (assetId, variants) ->
                val urls = variants.map { it.first }
                val preview = urls.firstOrNull { it.contains("AAAAM/") }
                    ?: urls.firstOrNull { it.contains("AAAAd/") }
                    ?: urls.first()
                val full = urls.firstOrNull { it.contains("AAAAC/") }
                    ?: urls.firstOrNull { it.contains("AAAAd/") }
                    ?: preview
                val title = variants.first().second
                    .replace('-', ' ')
                    .replace('_', ' ')
                    .ifBlank { "Tenor GIF" }

                TenorGifItem(
                    id = assetId,
                    title = title,
                    fullUrl = full,
                    previewUrl = preview
                )
            }
    }

    private fun foldTurkishChars(input: String): String {
        return input
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
    }

    private companion object {
        const val TAG = "TenorRepository"
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        val TENOR_GIF_REGEX = Regex(
            """https://media[0-9]*\.tenor\.com/(?:m/)?([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)\.gif"""
        )
    }
}
