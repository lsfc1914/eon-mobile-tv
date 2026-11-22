package com.example.eontv

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface EonApiService {

    @GET("v1/channels")
    suspend fun getChannels(
        @Header("Authorization") bearerToken: String,
        @Query("channelType") channelType: String = "TV",
        @Query("channelSort") channelSort: String = "POSITION"
    ): List<TvChannel>

    @GET("epg")
    suspend fun getEpg(
        @Header("Authorization") bearerToken: String,
        @Query("id") channelId: Int,
        @Query("date") date: String
    ): List<EpgEvent>
}

fun createEonOkHttpClient(userAgent: String): OkHttpClient {
    val headerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://eontv.bg")
            .header("Referer", "https://eontv.bg/")
            .header("User-Agent", userAgent)
            .build()
        chain.proceed(request)
    }

    return OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .build()
}
