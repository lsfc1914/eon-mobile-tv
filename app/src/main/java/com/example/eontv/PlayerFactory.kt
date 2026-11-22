package com.example.eontv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource

@OptIn(UnstableApi::class)
fun createEonExoPlayer(
    context: Context,
    userAgent: String
): ExoPlayer {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(
            mapOf(
                "Origin" to "https://eontv.bg",
                "Referer" to "https://eontv.bg/"
            )
        )

    val mediaSourceFactory = HlsMediaSource.Factory(httpDataSourceFactory)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
}
