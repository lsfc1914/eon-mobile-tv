package com.example.eonmobiletv.player

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.eonmobiletv.model.TvChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val retryMessage: String? = null,
    val retryCount: Int = 0
)

data class TrackOption(
    val name: String,
    val detail: String?,
    val isSelected: Boolean,
    val trackGroup: TrackGroup
)

@UnstableApi
class EonPlayerController(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("EonMobilePlayer/1.0 (Android)")
        .setConnectTimeoutMs(12_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            4_000,
            20_000,
            1_200,
            2_500
        )
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(appContext).setDataSourceFactory(httpFactory))
        .setLoadControl(loadControl)
        .build().apply {
            playWhenReady = true
            setWakeMode(C.WAKE_MODE_NETWORK)
        }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var currentChannel: TvChannel? = null
    private var currentUrl: String? = null
    private var pausedByUser = false
    private var retryJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _playerState.update { prev ->
                prev.copy(
                    isBuffering = buffering,
                    retryMessage = if (playbackState == Player.STATE_READY) null else prev.retryMessage,
                    retryCount = if (playbackState == Player.STATE_READY) 0 else prev.retryCount
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            triggerAutoRetry(error.localizedMessage ?: "Прекъснат поток")
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun playChannel(channel: TvChannel, streamUrl: String = channel.streamUrl) {
        retryJob?.cancel()
        currentChannel = channel
        currentUrl = streamUrl
        pausedByUser = false
        _playerState.update {
            it.copy(
                isBuffering = true,
                retryMessage = null,
                retryCount = 0
            )
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pausedByUser = true
            exoPlayer.pause()
        } else {
            pausedByUser = false
            if (exoPlayer.playbackState == Player.STATE_IDLE || _playerState.value.retryMessage != null) {
                val channel = currentChannel
                val url = currentUrl
                if (channel != null && url != null) playChannel(channel, url) else exoPlayer.play()
            } else {
                exoPlayer.play()
            }
        }
    }

    fun pauseForSleepTimer() {
        pausedByUser = true
        exoPlayer.pause()
    }

    fun seekBy(deltaMs: Long) {
        if (exoPlayer.playbackState == Player.STATE_IDLE) return
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET) return
        val upperBound = if (duration > 0) duration else exoPlayer.currentPosition
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, upperBound)
        exoPlayer.seekTo(target)
    }

    fun onHostStart() {
        if (!pausedByUser && currentChannel != null) {
            if (exoPlayer.playbackState == Player.STATE_IDLE || _playerState.value.retryMessage != null) {
                val channel = currentChannel
                val url = currentUrl
                if (channel != null && url != null) playChannel(channel, url)
            } else {
                exoPlayer.play()
            }
        }
    }

    fun onHostStop() {
        retryJob?.cancel()
        exoPlayer.pause()
    }

    fun getSystemVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    fun setSystemVolumeFraction(fraction: Float): Float {
        val clamped = fraction.coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (clamped * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return target.toFloat() / max.toFloat()
    }

    // ---------- Аудио / субтитри потоци ----------

    fun audioTrackOptions(): List<TrackOption> =
        exoPlayer.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isTrackSupported(0) }
            .map { group ->
                val format = group.getTrackFormat(0)
                TrackOption(
                    name = format.label?.takeIf { it.isNotBlank() }
                        ?: format.language?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                        ?: "Аудио поток",
                    detail = audioLayoutLabel(format),
                    isSelected = group.isTrackSelected(0),
                    trackGroup = group.mediaTrackGroup
                )
            }

    fun textTrackOptions(): List<TrackOption> =
        exoPlayer.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT && it.isTrackSupported(0) }
            .map { group ->
                val format = group.getTrackFormat(0)
                TrackOption(
                    name = format.label?.takeIf { it.isNotBlank() }
                        ?: format.language?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                        ?: "Субтитри",
                    detail = null,
                    isSelected = group.isTrackSelected(0),
                    trackGroup = group.mediaTrackGroup
                )
            }

    fun selectAudioTrack(option: TrackOption) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, 0))
            .build()
    }

    fun selectTextTrack(option: TrackOption) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, 0))
            .build()
    }

    fun disableSubtitles() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun audioLayoutLabel(format: Format): String {
        val layout = when (format.channelCount ?: 0) {
            1 -> "Моно"
            2 -> "Стерео"
            6 -> "5.1"
            8 -> "7.1"
            else -> null
        }
        val codec = when (format.sampleMimeType) {
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_AC3 -> "AC-3"
            MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_OPUS -> "Opus"
            else -> null
        }
        return listOfNotNull(layout, codec).joinToString(" • ")
    }

    private fun triggerAutoRetry(reason: String) {
        val channel = currentChannel ?: return
        retryJob?.cancel()
        retryJob = scope.launch {
            var attempt = _playerState.value.retryCount
            while (!pausedByUser) {
                attempt += 1
                val waitMs = (attempt * 2_000L).coerceAtMost(12_000L)
                _playerState.update {
                    it.copy(
                        isBuffering = true,
                        retryMessage = "$reason — автоматично презареждане (#$attempt)...",
                        retryCount = attempt
                    )
                }
                delay(waitMs)
                exoPlayer.setMediaItem(MediaItem.fromUri(currentUrl ?: channel.streamUrl))
                exoPlayer.prepare()
                exoPlayer.play()
                delay(3_500L)
                if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.isPlaying) {
                    _playerState.update { it.copy(retryMessage = null, retryCount = 0) }
                    return@launch
                }
            }
        }
    }

    fun release() {
        retryJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
