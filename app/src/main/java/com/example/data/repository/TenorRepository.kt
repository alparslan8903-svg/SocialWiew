package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.TenorGifItem
import com.example.data.remote.TenorApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

sealed interface TenorUiState {
    data object Idle : TenorUiState
    data object Loading : TenorUiState
    data object MissingApiKey : TenorUiState
    data class Success(val gifs: List<TenorGifItem>) : TenorUiState
    data class Error(val message: String) : TenorUiState
}

class TenorRepository(
    private val apiKey: String = BuildConfig.TENOR_API_KEY
) {
    private val service: TenorApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl("https://tenor.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TenorApiService::class.java)
    }

    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && apiKey != "MY_TENOR_API_KEY"
    }

    suspend fun loadGifs(query: String): TenorUiState {
        if (!isConfigured()) {
            Log.w("TenorRepository", "TENOR_API_KEY is missing or still set to placeholder.")
            return TenorUiState.MissingApiKey
        }
        return try {
            val response = if (query.isBlank()) {
                service.getFeaturedGifs(apiKey = apiKey)
            } else {
                service.searchGifs(query = query.trim(), apiKey = apiKey)
            }
            val items = response.results.mapNotNull { result ->
                val full = result.mediaFormats.gif?.url ?: result.mediaFormats.tinygif?.url
                val preview = result.mediaFormats.tinygif?.url ?: result.mediaFormats.nanogif?.url ?: full
                if (full.isNullOrBlank() || preview.isNullOrBlank()) null
                else TenorGifItem(
                    id = result.id,
                    title = result.contentDescription.ifBlank { result.title }.ifBlank { "GIF" },
                    fullUrl = full,
                    previewUrl = preview
                )
            }
            TenorUiState.Success(items)
        } catch (e: Exception) {
            Log.e("TenorRepository", "Tenor API error", e)
            TenorUiState.Error(e.localizedMessage ?: "Tenor GIF'leri yüklenemedi.")
        }
    }
}
