package com.example.eonmobiletv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.eonmobiletv.model.TvChannel
import com.example.eonmobiletv.player.EonPlayerController
import com.example.eonmobiletv.ui.EonTvViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EonBgDark = Color(0xFF0A0D12)
private val EonCardDark = Color(0xFF131923)
private val EonSelectedDark = Color(0xFF1A2838)
private val EonCyan = Color(0xFF00D4E6)
private val EonLiveRed = Color(0xFFFF3B4E)

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: EonTvViewModel by viewModels()
    private lateinit var playerController: EonPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        playerController = EonPlayerController(applicationContext, controllerScope)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = EonCyan,
                    secondary = EonLiveRed,
                    background = EonBgDark,
                    surface = EonCardDark
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EonTvScreen(
                        viewModel = viewModel,
                        playerController = playerController
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.release()
    }
}

@UnstableApi
@Composable
fun EonTvScreen(
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
    val playerState by playerController.playerState.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(0.75f) }
    var volumeFraction by remember { mutableFloatStateOf(playerController.getSystemVolumeFraction()) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var showM3uUrlDialog by remember { mutableStateOf(false) }
    var showEpgUrlDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(activeChannel?.id) {
        activeChannel?.let { playerController.playChannel(it) }
    }

    LaunchedEffect(isFullscreen, activity) {
        val act = activity ?: return@LaunchedEffect
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
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

    if (showM3uUrlDialog) {
        UrlInputDialog(
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
        UrlInputDialog(
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

    alertText?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlert() },
            containerColor = EonCardDark,
            title = { Text("EON TV Player", color = Color.White) },
            text = { Text(msg, color = Color(0xFFD0D7DE)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAlert() }) {
                    Text("OK", color = EonCyan)
                }
            }
        )
    }

    if (isFullscreen) {
        EonVideoStage(
            playerController = playerController,
            channel = activeChannel,
            isPlaying = playerState.isPlaying,
            isBuffering = playerState.isBuffering,
            retryMessage = playerState.retryMessage,
            isFullscreen = true,
            showOverlay = showOverlay,
            brightness = brightness,
            volumeFraction = volumeFraction,
            gestureLabel = gestureLabel,
            onToggleOverlay = { showOverlay = !showOverlay },
            onTogglePlayPause = { playerController.togglePlayPause() },
            onToggleFullscreen = { isFullscreen = false },
            onPrevChannel = { viewModel.stepChannel(-1) },
            onNextChannel = { viewModel.stepChannel(1) },
            onToggleFavorite = { activeChannel?.let { viewModel.toggleFavorite(it.id) } },
            onBrightnessChange = updateBrightness,
            onVolumeChange = updateVolume,
            modifier = Modifier.fillMaxSize()
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

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(EonBgDark)) {
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                EonVideoStage(
                    playerController = playerController,
                    channel = activeChannel,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    retryMessage = playerState.retryMessage,
                    isFullscreen = false,
                    showOverlay = showOverlay,
                    brightness = brightness,
                    volumeFraction = volumeFraction,
                    gestureLabel = gestureLabel,
                    onToggleOverlay = { showOverlay = !showOverlay },
                    onTogglePlayPause = { playerController.togglePlayPause() },
                    onToggleFullscreen = { isFullscreen = true },
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
                onSelectCategory = viewModel::selectCategory,
                onToggleFavorites = viewModel::toggleFavoritesFilter,
                onSearchChange = viewModel::updateSearch,
                onSelectChannel = viewModel::selectChannel,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenM3uUrlDialog = { showM3uUrlDialog = true },
                onPickM3uFile = { m3uFilePicker.launch(arrayOf("*/*")) },
                onOpenEpgUrlDialog = { showEpgUrlDialog = true },
                onPickEpgFile = openEpgFile,
                modifier = Modifier.weight(0.42f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(EonBgDark)) {
            EonVideoStage(
                playerController = playerController,
                channel = activeChannel,
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                retryMessage = playerState.retryMessage,
                isFullscreen = false,
                showOverlay = showOverlay,
                brightness = brightness,
                volumeFraction = volumeFraction,
                gestureLabel = gestureLabel,
                onToggleOverlay = { showOverlay = !showOverlay },
                onTogglePlayPause = { playerController.togglePlayPause() },
                onToggleFullscreen = { isFullscreen = true },
                onPrevChannel = { viewModel.stepChannel(-1) },
                onNextChannel = { viewModel.stepChannel(1) },
                onToggleFavorite = { activeChannel?.let { viewModel.toggleFavorite(it.id) } },
                onBrightnessChange = updateBrightness,
                onVolumeChange = updateVolume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
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
                onSelectCategory = viewModel::selectCategory,
                onToggleFavorites = viewModel::toggleFavoritesFilter,
                onSearchChange = viewModel::updateSearch,
                onSelectChannel = viewModel::selectChannel,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenM3uUrlDialog = { showM3uUrlDialog = true },
                onPickM3uFile = { m3uFilePicker.launch(arrayOf("*/*")) },
                onOpenEpgUrlDialog = { showEpgUrlDialog = true },
                onPickEpgFile = openEpgFile,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@UnstableApi
@Composable
private fun EonVideoStage(
    playerController: EonPlayerController,
    channel: TvChannel?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    retryMessage: String?,
    isFullscreen: Boolean,
    showOverlay: Boolean,
    brightness: Float,
    volumeFraction: Float,
    gestureLabel: String?,
    onToggleOverlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(brightness, volumeFraction) {
                detectTapGestures(
                    onTap = { onToggleOverlay() },
                    onDoubleTap = { onToggleFullscreen() }
                )
            }
            .pointerInput(brightness, volumeFraction) {
                var deltaAccumulator = 0f
                var initialValue = 0f
                var leftZone = true
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        deltaAccumulator = 0f
                        leftZone = offset.x < size.width / 2f
                        initialValue = if (leftZone) brightness else volumeFraction
                    },
                    onVerticalDrag = { _, dragAmount ->
                        deltaAccumulator -= dragAmount / size.height.coerceAtLeast(1)
                        val updated = (initialValue + deltaAccumulator).coerceIn(0f, 1f)
                        if (leftZone) onBrightnessChange(updated) else onVolumeChange(updated)
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

        if (isBuffering) {
            CircularProgressIndicator(
                color = EonCyan,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        gestureLabel?.let { hud ->
            val isBrightnessHud = hud.startsWith("Яркост")
            Surface(
                color = Color(0xD9101622),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .border(1.dp, EonCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = if (isBrightnessHud) Icons.Default.Brightness6 else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = EonCyan,
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

        retryMessage?.let { msg ->
            Surface(
                color = Color(0xE0311017),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp, start = 12.dp, end = 12.dp)
            ) {
                Text(
                    text = msg,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showOverlay,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xA6000000),
                                Color.Transparent,
                                Color(0xC2000000)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EonLiveRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EON LIVE",
                        color = EonLiveRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = channel?.let { "${it.number}. ${it.name}" } ?: "Избери канал",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (channel?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Любим канал",
                            tint = if (channel?.isFavorite == true) EonLiveRed else Color.White
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

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    IconButton(onClick = onPrevChannel) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Предходен канал",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Пауза / Пусни",
                            tint = EonCyan,
                            modifier = Modifier.size(34.dp)
                        )
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

@Composable
private fun EonNowNextBar(channel: TvChannel) {
    val timeFmt = remember {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }
    val current = channel.currentProgramme
    val next = channel.nextProgramme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EonCardDark)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "СЕГА",
                color = EonCyan,
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
                color = EonCyan,
                trackColor = Color(0xFF253244),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }

        next?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "СЛЕДВА (${timeFmt.format(it.startTime)}): ${it.title}",
                color = Color(0xFF9FB0C3),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EonChannelListPane(
    channels: List<TvChannel>,
    categories: List<String>,
    selectedCategory: String,
    onlyFavorites: Boolean,
    searchQuery: String,
    activeChannelId: String?,
    isLoading: Boolean,
    onSelectCategory: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenM3uUrlDialog: () -> Unit,
    onPickM3uFile: () -> Unit,
    onOpenEpgUrlDialog: () -> Unit,
    onPickEpgFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Търси в EON канали...", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EonCyan) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorites) {
                Icon(
                    imageVector = if (onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Любими",
                    tint = if (onlyFavorites) EonLiveRed else Color.White
                )
            }
            IconButton(onClick = onOpenM3uUrlDialog) {
                Icon(Icons.Default.Link, contentDescription = "M3U URL", tint = EonCyan)
            }
            IconButton(onClick = onPickM3uFile) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Локален M3U", tint = EonCyan)
            }
            IconButton(onClick = onOpenEpgUrlDialog) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "EPG URL", tint = EonCyan)
            }
            IconButton(onClick = onPickEpgFile) {
                Icon(Icons.Default.UploadFile, contentDescription = "Локален EPG", tint = EonCyan)
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                color = EonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it }) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = EonCyan
                    )
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.id }) { channel ->
                val selected = channel.id == activeChannelId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) EonSelectedDark else EonCardDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (selected) Modifier.border(1.dp, EonCyan.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable { onSelectChannel(channel) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EonBgDark)
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${channel.number}. ${channel.name}",
                                    color = if (selected) EonCyan else Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = channel.groupTitle,
                                    color = Color(0xFF8DA2B5),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            channel.currentProgramme?.let { cur ->
                                Text(
                                    text = "Сега: ${cur.title}",
                                    color = Color(0xFFD6E2EC),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            channel.nextProgramme?.let { nxt ->
                                Text(
                                    text = "Следва: ${nxt.title}",
                                    color = Color(0xFF7B90A4),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onToggleFavorite(channel.id) }) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Любим",
                                tint = if (channel.isFavorite) EonLiveRed else Color(0xFF8DA2B5)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlInputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EonCardDark,
        title = { Text(title, color = Color.White) },
        text = {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text(placeholder, maxLines = 1) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(urlInput) },
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EonCyan, contentColor = EonBgDark)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отказ", color = Color(0xFF9FB0C3))
            }
        }
    )
}
