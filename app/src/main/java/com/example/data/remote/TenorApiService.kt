package com.example.data.remote

import com.example.data.model.TenorSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TenorApiService {
    @GET("v2/featured")
    suspend fun getFeaturedGifs(
        @Query("key") apiKey: String,
        @Query("client_key") clientKey: String = "vibereel_android",
        @Query("limit") limit: Int = 24,
        @Query("media_filter") mediaFilter: String = "gif,tinygif",
        @Query("locale") locale: String = "tr_TR"
    ): TenorSearchResponse

    @GET("v2/search")
    suspend fun searchGifs(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("client_key") clientKey: String = "vibereel_android",
        @Query("limit") limit: Int = 24,
        @Query("media_filter") mediaFilter: String = "gif,tinygif",
        @Query("locale") locale: String = "tr_TR"
    ): TenorSearchResponse
}
