package com.example.eonmobiletv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.example.eonmobiletv.player.EonPlayerController
import com.example.eonmobiletv.ui.ChannelNumberPadDialog
import com.example.eonmobiletv.ui.EonChannelListPane
import com.example.eonmobiletv.ui.EonNowNextBar
import com.example.eonmobiletv.ui.EonSleepTimerDialog
import com.example.eonmobiletv.ui.EonTracksDialog
import com.example.eonmobiletv.ui.EonTvViewModel
import com.example.eonmobiletv.ui.EonUrlInputDialog
import com.example.eonmobiletv.ui.EonVideoStage
import com.example.eonmobiletv.ui.theme.EonColors
import com.example.eonmobiletv.ui.theme.EonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.util.Locale

internal val IsInPipMode = mutableStateOf(false)

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: EonTvViewModel by viewModels()
    private lateinit var playerController: EonPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        playerController = EonPlayerController(applicationContext, controllerScope)

        setContent {
            EonTheme {
                EonTvScreen(viewModel = viewModel, playerController = playerController)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        IsInPipMode.value = isInPictureInPictureMode
    }

    internal fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.release()
    }
}

@UnstableApi
@Composable
private fun EonTvScreen(
    viewModel: EonTvViewModel,
    playerController: EonPlayerController
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val channels by viewModel.visibleChannels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val selectedCategory by viewModel.selectedCategoryState.collectAsState()
    val onlyFavorites by viewModel.onlyFavoritesState.collectAsState()
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val isLoading by viewModel.isLoadingState.collectAsState()
    val alertText by viewModel.alertTextState.collectAsState()
    val playTick by viewModel.playTickState.collectAsState()
    val catchupRequest by viewModel.catchupRequestState.collectAsState()
    val sleepEndsAt by viewModel.sleepTimerEndsAtState.collectAsState()
    val epgMap by viewModel.epgMapState.collectAsState()
    val playerState by playerController.playerState.collectAsState()
    val isInPip by IsInPipMode

    var isFullscreen by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(0.75f) }
    var volumeFraction by remember { mutableFloatStateOf(playerController.getSystemVolumeFraction()) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var catchupTitle by remember { mutableStateOf<String?>(null) }
    var isGuideMode by remember { mutableStateOf(false) }
    var showM3uUrlDialog by remember { mutableStateOf(false) }
    var showEpgUrlDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var showNumberPadDialog by remember { mutableStateOf(false) }
    var tracksRefresh by remember { mutableStateOf(0) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val sleepRemainingText: String? = sleepEndsAt?.let { ends ->
        val remain = ends - nowMs
        if (remain <= 0L) {
            null
        } else {
            String.format(Locale.US, "%d:%02d", remain / 60_000, (remain % 60_000) / 1_000)
        }
    }
    val activeSleepMinutes: Int? = sleepEndsAt?.let { ends ->
        (((ends - nowMs) + 59_999) / 60_000).toInt()
    }

    val m3uFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadFromUri(it) }
    }

    val epgFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadEpgFromUri(it) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    playerController.onHostStart()
                    viewModel.reloadEpgFromCache()
                }
                Lifecycle.Event.ON_STOP -> playerController.onHostStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeChannel?.id, playTick) {
        catchupTitle = null
        activeChannel?.let { playerController.playChannel(it) }
    }

    LaunchedEffect(catchupRequest) {
        val request = catchupRequest ?: return@LaunchedEffect
        playerController.playChannel(request.channel, request.streamUrl)
        catchupTitle = request.programmeTitle
        viewModel.consumeCatchup()
    }

    LaunchedEffect(sleepEndsAt) {
        val ends = sleepEndsAt ?: return@LaunchedEffect
        val wait = ends - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        playerController.pauseForSleepTimer()
        viewModel.cancelSleepTimer()
        showOverlay = true
        gestureLabel = "Таймерът за заспиване изтече"
    }

    LaunchedEffect(showOverlay, isFullscreen, isInPip) {
        if (showOverlay && isFullscreen && !isInPip) {
            delay(3_500L)
            showOverlay = false
        }
    }

    LaunchedEffect(isFullscreen, activity) {
        val act = activity ?: return@LaunchedEffect
        val window = act.window
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(gestureLabel) {
        if (gestureLabel != null) {
            delay(1_100L)
            gestureLabel = null
        }
    }

    val updateBrightness: (Float) -> Unit = { newVal ->
        val clamped = newVal.coerceIn(0.08f, 1.0f)
        brightness = clamped
        activity?.window?.let { win ->
            val attrs = win.attributes
            attrs.screenBrightness = clamped
            win.attributes = attrs
        }
        gestureLabel = "Яркост: ${(clamped * 100).toInt()}%"
    }

    val updateVolume: (Float) -> Unit = { newVal ->
        val updated = playerController.setSystemVolumeFraction(newVal)
        volumeFraction = updated
        gestureLabel = "Звук: ${(updated * 100).toInt()}%"
    }

    val updateSeek: (Long) -> Unit = { deltaMs ->
        playerController.seekBy(deltaMs)
        gestureLabel = if (deltaMs < 0) "−10 сек" else "+10 сек"
    }

    if (showM3uUrlDialog) {
        EonUrlInputDialog(
            title = "Зареди M3U / M3U8 от URL",
            placeholder = "https://example.com/playlist.m3u",
            confirmLabel = "Зареди M3U",
            onDismiss = { showM3uUrlDialog = false },
            onSubmit = { url ->
                showM3uUrlDialog = false
                viewModel.loadFromUrl(url)
            }
        )
    }

    if (showEpgUrlDialog) {
        EonUrlInputDialog(
            title = "Зареди XMLTV / .xml.gz EPG от URL",
            placeholder = "https://example.com/epg.xml.gz",
            confirmLabel = "Зареди EPG",
            onDismiss = { showEpgUrlDialog = false },
            onSubmit = { url ->
                showEpgUrlDialog = false
                viewModel.loadEpgFromUrl(url)
            }
        )
    }

    if (showSleepTimerDialog) {
        EonSleepTimerDialog(
            activeMinutes = activeSleepMinutes,
            onSelect = { minutes ->
                viewModel.startSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showTracksDialog) {
        val audioOptions = remember(showTracksDialog, tracksRefresh) {
            playerController.audioTrackOptions()
        }
        val textOptions = remember(showTracksDialog, tracksRefresh) {
            playerController.textTrackOptions()
        }
        EonTracksDialog(
            audioOptions = audioOptions,
            textOptions = textOptions,
            onSelectAudio = { option ->
                playerController.selectAudioTrack(option)
                tracksRefresh += 1
            },
            onSelectText = { option ->
                playerController.selectTextTrack(option)
                tracksRefresh += 1
            },
            onDisableSubtitles = {
                playerController.disableSubtitles()
                tracksRefresh += 1
            },
            onDismiss = { showTracksDialog = false }
        )
    }

    if (showNumberPadDialog) {
        ChannelNumberPadDialog(
            channels = channels,
            onDismiss = { showNumberPadDialog = false },
            onSelect = { number ->
                if (viewModel.selectChannelByNumber(number)) {
                    showNumberPadDialog = false
                }
            }
        )
    }

    alertText?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlert() },
            containerColor = EonColors.Card,
            title = { Text("EON TV Player", color = Color.White) },
            text = { Text(msg, color = Color(0xFFD0D7DE)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAlert() }) {
                    Text("OK", color = EonColors.Accent)
                }
            }
        )
    }

    if (isInPip) {
        EonVideoStage(
            playerController = playerController,
            channel = activeChannel,
            playerState = playerState,
            catchupTitle = null,
            sleepRemainingText = null,
            isFullscreen = false,
            showOverlay = false,
            brightness = brightness,
            volumeFraction = volumeFraction,
            gestureLabel = null,
            programme = null,
            onToggleOverlay = {},
            onTogglePlayPause = {},
            onSeek = {},
            onToggleFullscreen = {},
            onEnterPip = {},
            onOpenTracks = {},
            onOpenSleepTimer = {},
            onResumeLive = {},
            onPrevChannel = {},
            onNextChannel = {},
            onToggleFavorite = {},
            onBrightnessChange = {},
            onVolumeChange = {},
            modifier = Modifier.fillMaxSize().background(Color.Black).testTag("pip-video-stage")
        )
        return
    }

    val openEpgFile = {
        epgFilePicker.launch(
            arrayOf(
                "application/xml",
                "text/xml",
                "application/gzip",
                "application/x-gzip",
                "application/octet-stream"
            )
        )
    }

    if (isFullscreen) {
        EonVideoStage(
            playerController = playerController,
            channel = activeChannel,
            playerState = playerState,
            catchupTitle = catchupTitle,
            sleepRemainingText = sleepRemainingText,
            isFullscreen = true,
            showOverlay = showOverlay,
            brightness = brightness,
            volumeFraction = volumeFraction,
            gestureLabel = gestureLabel,
            programme = activeChannel?.currentProgramme,
            onToggleOverlay = { showOverlay = !showOverlay },
            onTogglePlayPause = { playerController.togglePlayPause() },
            onSeek = updateSeek,
            onToggleFullscreen = { isFullscreen = false },
            onEnterPip = { (context as? MainActivity)?.enterPipMode() },
            onOpenTracks = { showTracksDialog = true },
            onOpenSleepTimer = { showSleepTimerDialog = true },
            onResumeLive = { viewModel.resumeLive() },
            onPrevChannel = { viewModel.stepChannel(-1) },
            onNextChannel = { viewModel.stepChannel(1) },
            onToggleFavorite = { activeChannel?.let { viewModel.toggleFavorite(it.id) } },
            onBrightnessChange = updateBrightness,
            onVolumeChange = updateVolume,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(EonColors.Page)) {
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                EonVideoStage(
                    playerController = playerController,
                    channel = activeChannel,
                    playerState = playerState,
                    catchupTitle = catchupTitle,
                    sleepRemainingText = sleepRemainingText,
                    isFullscreen = false,
                    showOverlay = showOverlay,
                    brightness = brightness,
                    volumeFraction = volumeFraction,
                    gestureLabel = gestureLabel,
                    programme = activeChannel?.currentProgramme,
                    onToggleOverlay = { showOverlay = !showOverlay },
                    onTogglePlayPause = { playerController.togglePlayPause() },
                    onSeek = updateSeek,
                    onToggleFullscreen = { isFullscreen = true },
                    onEnterPip = { (context as? MainActivity)?.enterPipMode() },
                    onOpenTracks = { showTracksDialog = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onResumeLive = { viewModel.resumeLive() },
                    onPrevChannel = { viewModel.stepChannel(-1) },
                    onNextChannel = { viewModel.stepChannel(1) },
                    onToggleFavorite = { activeChannel?.let { viewModel.toggleFavorite(it.id) } },
                    onBrightnessChange = updateBrightness,
                    onVolumeChange = updateVolume,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                activeChannel?.let { EonNowNextBar(channel = it) }
            }

            EonChannelListPane(
                channels = channels,
                categories = categories,
                selectedCategory = selectedCategory,
                onlyFavorites = onlyFavorites,
                searchQuery = searchQuery,
                activeChannelId = activeChannel?.id,
                isLoading = isLoading,
                isGuideMode = isGuideMode,
                epgMap = epgMap,
                onSelectCategory = viewModel::selectCategory,
                onToggleFavorites = viewModel::toggleFavoritesFilter,
                onSearchChange = viewModel::updateSearch,
                onSelectChannel = viewModel::selectChannel,
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleGuideMode = { isGuideMode = !isGuideMode },
                onPlayLive = viewModel::selectChannel,
                onPlayCatchup = viewModel::requestCatchup,
                onOpenNumberPad = { showNumberPadDialog = true },
                onOpenM3uUrlDialog = { showM3uUrlDialog = true },
                onPickM3uFile = { m3uFilePicker.launch(arrayOf("*/*")) },
                onOpenEpgUrlDialog = { showEpgUrlDialog = true },
                onPickEpgFile = openEpgFile,
                modifier = Modifier.weight(0.42f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(EonColors.Page)) {
            EonVideoStage(
                playerController = playerController,
                channel = activeChannel,
                playerState = playerState,
                catchupTitle = catchupTitle,
                sleepRemainingText = sleepRemainingText,
                isFullscreen = false,
                showOverlay = showOverlay,
                brightness = brightness,
                volumeFraction = volumeFraction,
                gestureLabel = gestureLabel,
                programme = activeChannel?.currentProgramme,
                onToggleOverlay = { showOverlay = !showOverlay },
                onTogglePlayPause = { playerController.togglePlayPause() },
                onSeek = updateSeek,
                onToggleFullscreen = { isFullscreen = true },
                onEnterPip = { (context as? MainActivity)?.enterPipMode() },
                onOpenTracks = { showTracksDialog = true },
                onOpenSleepTimer = { showSleepTimerDialog = true },
                onResumeLive = { viewModel.resumeLive() },
                onPrevChannel = { viewModel.stepChannel(-1) },
                onNextChannel = { viewModel.stepChannel(1) },
                onToggleFavorite = { activeChannel?.let { viewModel.toggleFavorite(it.id) } },
                onBrightnessChange = updateBrightness,
                onVolumeChange = updateVolume,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            activeChannel?.let { EonNowNextBar(channel = it) }

            EonChannelListPane(
                channels = channels,
                categories = categories,
                selectedCategory = selectedCategory,
                onlyFavorites = onlyFavorites,
                searchQuery = searchQuery,
                activeChannelId = activeChannel?.id,
                isLoading = isLoading,
                isGuideMode = isGuideMode,
                epgMap = epgMap,
                onSelectCategory = viewModel::selectCategory,
                onToggleFavorites = viewModel::toggleFavoritesFilter,
                onSearchChange = viewModel::updateSearch,
                onSelectChannel = viewModel::selectChannel,
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleGuideMode = { isGuideMode = !isGuideMode },
                onPlayLive = viewModel::selectChannel,
                onPlayCatchup = viewModel::requestCatchup,
                onOpenNumberPad = { showNumberPadDialog = true },
                onOpenM3uUrlDialog = { showM3uUrlDialog = true },
                onPickM3uFile = { m3uFilePicker.launch(arrayOf("*/*")) },
                onOpenEpgUrlDialog = { showEpgUrlDialog = true },
                onPickEpgFile = openEpgFile,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
