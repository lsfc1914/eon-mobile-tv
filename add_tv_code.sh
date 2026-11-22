#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="${1:-app/src/main/java/com/example/eontv}"
mkdir -p "$SRC_DIR" ".github/workflows"

cat <<'KOTLIN' > "$SRC_DIR/Models.kt"
package com.example.eontv

import kotlinx.serialization.Serializable

@Serializable
data class TvChannel(
    val id: Int,
    val name: String,
    val ageRating: String? = null
)

@Serializable
data class EpgImage(
    val path: String,
    val width: Int? = null,
    val height: Int? = null,
    val size: String? = null
)

@Serializable
data class EpgEvent(
    val id: Long,
    val channelId: Int,
    val title: String,
    val originalTitle: String? = null,
    val shortDescription: String? = null,
    val startTime: Long,
    val endTime: Long,
    val liveBroadcast: Boolean = false,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val slug: String? = null,
    val images: List<EpgImage> = emptyList()
)

data class ChannelWithEpg(
    val channel: TvChannel,
    val currentEvent: EpgEvent?,
    val nextEvent: EpgEvent?,
    val progress: Float,
    val allEvents: List<EpgEvent>
)
KOTLIN

cat <<'KOTLIN' > "$SRC_DIR/EonApiService.kt"
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
KOTLIN

cat <<'KOTLIN' > "$SRC_DIR/TvGuideRepository.kt"
package com.example.eontv

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZoneId

class TvGuideRepository(
    private val api: EonApiService,
    private val tokenProvider: suspend () -> String
) {
    private val requestSemaphore = Semaphore(permits = 6)

    suspend fun loadChannelsWithEpg(
        date: LocalDate = LocalDate.now(ZoneId.of("Europe/Sofia")),
        nowMillis: Long = System.currentTimeMillis()
    ): List<ChannelWithEpg> = coroutineScope {
        val bearer = formatBearer(tokenProvider())
        val channels = api.getChannels(bearerToken = bearer)
        val dateStr = date.toString()

        channels.map { channel ->
            async {
                requestSemaphore.withPermit {
                    val events = runCatching {
                        api.getEpg(
                            bearerToken = bearer,
                            channelId = channel.id,
                            date = dateStr
                        )
                    }.getOrDefault(emptyList()).sortedBy { it.startTime }

                    val (current, next, progress) = resolveNowNext(events, nowMillis)
                    ChannelWithEpg(
                        channel = channel,
                        currentEvent = current,
                        nextEvent = next,
                        progress = progress,
                        allEvents = events
                    )
                }
            }
        }.awaitAll()
    }

    private fun formatBearer(rawToken: String): String =
        if (rawToken.startsWith("Bearer ", ignoreCase = true)) rawToken else "Bearer $rawToken"

    private fun resolveNowNext(
        sortedEvents: List<EpgEvent>,
        nowMillis: Long
    ): Triple<EpgEvent?, EpgEvent?, Float> {
        val currentIndex = sortedEvents.indexOfFirst { nowMillis in it.startTime until it.endTime }
        val current = if (currentIndex >= 0) sortedEvents[currentIndex] else null
        val next = when {
            currentIndex >= 0 && currentIndex + 1 < sortedEvents.size -> sortedEvents[currentIndex + 1]
            currentIndex == -1 -> sortedEvents.firstOrNull { it.startTime > nowMillis }
            else -> null
        }

        val progress = current?.let {
            val duration = (it.endTime - it.startTime).coerceAtLeast(1L)
            ((nowMillis - it.startTime).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } ?: 0f

        return Triple(current, next, progress)
    }
}
KOTLIN

cat <<'KOTLIN' > "$SRC_DIR/PlayerFactory.kt"
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
KOTLIN

cat <<'KOTLIN' > "$SRC_DIR/TimeUtils.kt"
package com.example.eontv

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val bgTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Sofia"))

fun formatEventTimeRange(startMillis: Long, endMillis: Long): String {
    val start = bgTimeFormatter.format(Instant.ofEpochMilli(startMillis))
    val end = bgTimeFormatter.format(Instant.ofEpochMilli(endMillis))
    return "$start - $end"
}
KOTLIN

cat <<'KOTLIN' > "$SRC_DIR/TvPlayerScreen.kt"
package com.example.eontv

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvPlayerScreen(
    channels: List<ChannelWithEpg>,
    userAgent: String,
    resolveStreamUri: suspend (channelId: Int) -> Uri?,
    onEventClick: (channelId: Int, event: EpgEvent) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val player = remember { createEonExoPlayer(context, userAgent) }
    var selectedChannelId by remember { mutableStateOf<Int?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var epgDialogChannel by remember { mutableStateOf<ChannelWithEpg?>(null) }

    val filteredChannels = remember(channels, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            channels
        } else {
            channels.filter { item ->
                item.channel.name.contains(q, ignoreCase = true) ||
                    (item.currentEvent?.title?.contains(q, ignoreCase = true) == true)
            }
        }
    }

    fun playChannel(channelId: Int, isRetry: Boolean = false) {
        if (isRefreshing) return
        if (!isRetry) retryCount = 0

        selectedChannelId = channelId
        statusMessage = "Loading stream..."

        scope.launch {
            isRefreshing = true
            try {
                player.stop()
                player.clearMediaItems()

                val streamUri = resolveStreamUri(channelId)
                if (streamUri == null) {
                    statusMessage = "Stream unavailable"
                    return@launch
                }

                player.setMediaItem(MediaItem.fromUri(streamUri))
                player.prepare()
                player.playWhenReady = true
                statusMessage = null
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            } finally {
                isRefreshing = false
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val isAuthOrExpiry = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .any { it.responseCode == 401 || it.responseCode == 403 }

                val currentId = selectedChannelId
                if (isAuthOrExpiry && currentId != null && retryCount < 2) {
                    retryCount++
                    playChannel(currentId, isRetry = true)
                } else {
                    statusMessage = "Playback failed (${error.errorCodeName})"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )

            statusMessage?.let { msg ->
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Search (${channels.size})") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(filteredChannels, key = { it.channel.id }) { item ->
                val isSelected = item.channel.id == selectedChannelId
                ChannelRow(
                    item = item,
                    isSelected = isSelected,
                    onClick = { playChannel(item.channel.id) },
                    onLongClick = { epgDialogChannel = item }
                )
                HorizontalDivider()
            }
        }
    }

    epgDialogChannel?.let { channelItem ->
        ChannelScheduleDialog(
            item = channelItem,
            onDismiss = { epgDialogChannel = null },
            onSelectEvent = { event ->
                epgDialogChannel = null
                onEventClick(channelItem.channel.id, event)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    item: ChannelWithEpg,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = item.channel.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.currentEvent?.title ?: "No EPG data",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (item.currentEvent != null) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        item.nextEvent?.let { next ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Next: ${next.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelScheduleDialog(
    item: ChannelWithEpg,
    onDismiss: () -> Unit,
    onSelectEvent: (EpgEvent) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = item.channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (item.allEvents.isEmpty()) {
                Text("No events for today.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(item.allEvents, key = { it.id }) { event ->
                        val isNow = event.id == item.currentEvent?.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isNow) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable { onSelectEvent(event) }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = formatEventTimeRange(event.startTime, event.endTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNow) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal
                            )
                            event.shortDescription?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
KOTLIN

cat <<'YAML' > ".github/workflows/android.yml"
name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: eon-tv-player-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
YAML
