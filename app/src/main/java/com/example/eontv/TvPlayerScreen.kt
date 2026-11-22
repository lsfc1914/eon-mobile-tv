package com.example.eontv

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
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
