package com.example.eonmobiletv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.eonmobiletv.model.EpgProgramme
import com.example.eonmobiletv.model.TvChannel
import com.example.eonmobiletv.player.EonPlayerController
import com.example.eonmobiletv.player.PlayerState
import com.example.eonmobiletv.ui.theme.EonColors
import java.time.format.DateTimeFormatter

private val EonEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@UnstableApi
@Composable
fun EonVideoStage(
    playerController: EonPlayerController,
    channel: TvChannel?,
    playerState: PlayerState,
    catchupTitle: String?,
    sleepRemainingText: String?,
    isFullscreen: Boolean,
    showOverlay: Boolean,
    brightness: Float,
    volumeFraction: Float,
    gestureLabel: String?,
    programme: EpgProgramme?,
    onToggleOverlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onResumeLive: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentBrightness by rememberUpdatedState(brightness)
    val currentVolume by rememberUpdatedState(volumeFraction)
    val currentOnBrightness by rememberUpdatedState(onBrightnessChange)
    val currentOnVolume by rememberUpdatedState(onVolumeChange)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnToggleOverlay by rememberUpdatedState(onToggleOverlay)
    val currentOnToggleFullscreen by rememberUpdatedState(onToggleFullscreen)

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnToggleOverlay() },
                    onDoubleTap = { offset ->
                        when {
                            offset.x < size.width / 3f -> currentOnSeek(-10_000L)
                            offset.x > size.width * 2f / 3f -> currentOnSeek(10_000L)
                            else -> currentOnToggleFullscreen()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var deltaAccumulator = 0f
                var initialValue = 0f
                var leftZone = true
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        deltaAccumulator = 0f
                        leftZone = offset.x < size.width / 2f
                        initialValue = if (leftZone) currentBrightness else currentVolume
                    },
                    onVerticalDrag = { _, dragAmount ->
                        deltaAccumulator -= dragAmount / size.height.coerceAtLeast(1)
                        val updated = (initialValue + deltaAccumulator).coerceIn(0f, 1f)
                        if (leftZone) currentOnBrightness(updated) else currentOnVolume(updated)
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerController.exoPlayer
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { view ->
                if (view.player !== playerController.exoPlayer) {
                    view.player = playerController.exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playerState.isBuffering) {
            CircularProgressIndicator(
                color = EonColors.Accent,
                modifier = Modifier.align(Alignment.Center).testTag("player-buffering")
            )
        }

        gestureLabel?.let { hud ->
            val isBrightnessHud = hud.startsWith("Яркост")
            val isVolumeHud = hud.startsWith("Звук")
            Surface(
                color = EonColors.HudBubble,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .border(1.dp, EonColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isBrightnessHud -> Icons.Default.Brightness6
                            isVolumeHud -> Icons.AutoMirrored.Filled.VolumeUp
                            else -> Icons.Default.FastForward
                        },
                        contentDescription = null,
                        tint = EonColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hud,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        playerState.retryMessage?.let { msg ->
            Surface(
                color = EonColors.DangerBubble,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 12.dp, end = 12.dp)
            ) {
                Text(
                    text = msg,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ---------- Оverlay ----------
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(tween(220, easing = EonEasing)),
            exit = fadeOut(tween(280, easing = EonEasing)),
            modifier = Modifier
                .fillMaxSize()
                .testTag("player-overlay")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                EonColors.ScrimTop,
                                Color(0x140D1117),
                                EonColors.ScrimBottom
                            )
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(tween(220, easing = EonEasing)) + slideInVertically(tween(220, easing = EonEasing)) { -it },
            exit = fadeOut(tween(280, easing = EonEasing)) + slideOutVertically(tween(280, easing = EonEasing)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EonColors.Page.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("player-top-bar")
            ) {
                LiveOrArchiveBadge(catchupTitle = catchupTitle)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = channel?.let { "${it.number}. ${it.name}" } ?: "Избери канал",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("player-channel-title")
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (channel?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Любим канал",
                        tint = if (channel?.isFavorite == true) EonColors.Favorite else Color.White
                    )
                }
                IconButton(onClick = onOpenTracks) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Аудио и субтитри",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Таймер за заспиване",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onEnterPip) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Картина в картината",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Пълен екран",
                        tint = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showOverlay && sleepRemainingText != null,
            enter = fadeIn(tween(220, easing = EonEasing)),
            exit = fadeOut(tween(280, easing = EonEasing)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 10.dp)
        ) {
            Surface(
                color = EonColors.HudBubble,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, EonColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).testTag("sleep-timer-chip")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = EonColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sleepRemainingText.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(tween(220, easing = EonEasing)) + slideInVertically(tween(220, easing = EonEasing)) { it },
            exit = fadeOut(tween(280, easing = EonEasing)) + slideOutVertically(tween(280, easing = EonEasing)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("player-bottom-bar"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xCC101622),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(1.dp, EonColors.Accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = catchupTitle?.let { "Архив: $it" }
                                    ?: programme?.let { "Сега: ${it.title}" }
                                    ?: "Няма EPG информация",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (catchupTitle != null) {
                                TextButton(onClick = onResumeLive) {
                                    Text("Върни се на живо", color = EonColors.Accent, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (catchupTitle == null && programme != null) {
                            LinearProgressIndicator(
                                progress = { programme.progressFraction() },
                                color = EonColors.Accent,
                                trackColor = EonColors.Border,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .testTag("player-programme-progress")
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onPrevChannel) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Предходен канал",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = onTogglePlayPause) {
                                AnimatedContent(
                                    targetState = playerState.isPlaying,
                                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                                    label = "playPauseIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Пауза / Пусни",
                                        tint = EonColors.Accent,
                                        modifier = Modifier.size(34.dp).testTag("player-play-pause-btn")
                                    )
                                }
                            }
                            IconButton(onClick = onNextChannel) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Следващ канал",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveOrArchiveBadge(catchupTitle: String?) {
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "livePulseAlpha"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    alpha = pulseAlpha
                    scaleX = 1f + 0.08f * (1f - pulseAlpha)
                    scaleY = 1f + 0.08f * (1f - pulseAlpha)
                }
                .clip(CircleShape)
                .background(if (catchupTitle == null) EonColors.Live else EonColors.Accent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (catchupTitle == null) "НА ЖИВО" else "АРХИВ",
            color = if (catchupTitle == null) EonColors.Live else EonColors.Accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun EonNowNextBar(channel: TvChannel) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
    val current = channel.currentProgramme
    val next = channel.nextProgramme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EonColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("now-next-bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "СЕГА",
                color = EonColors.Accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = current?.let {
                    "${timeFmt.format(it.startTime)}–${timeFmt.format(it.endTime)} • ${it.title}"
                } ?: "Няма EPG информация",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        current?.let {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { it.progressFraction() },
                color = EonColors.Accent,
                trackColor = EonColors.Border,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        next?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "СЛЕДВА (${timeFmt.format(it.startTime)}): ${it.title}",
                color = EonColors.TextSoft,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
