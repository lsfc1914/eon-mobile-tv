package com.example.eonmobiletv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.eonmobiletv.data.CatchupHelper
import com.example.eonmobiletv.data.EonPreferencesRepository
import com.example.eonmobiletv.data.EpgCacheStore
import com.example.eonmobiletv.data.GzipStreamHelper
import com.example.eonmobiletv.data.M3uParser
import com.example.eonmobiletv.data.XmlTvParser
import com.example.eonmobiletv.model.CatchupRequest
import com.example.eonmobiletv.model.EpgProgramme
import com.example.eonmobiletv.model.TvChannel
import com.example.eonmobiletv.worker.EpgRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.Instant

class EonTvViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = EonPreferencesRepository(application)
    private val epgCacheStore = EpgCacheStore(application)

    private val allChannels = MutableStateFlow<List<TvChannel>>(emptyList())
    private val epgByTvgId = MutableStateFlow<Map<String, List<EpgProgramme>>>(emptyMap())
    private val selectedCategory = MutableStateFlow("Всички")
    private val onlyFavorites = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val activeChannelId = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)
    private val alertText = MutableStateFlow<String?>(null)
    private val playTick = MutableStateFlow(0)
    private val sleepTimerEndsAt = MutableStateFlow<Long?>(null)
    private val catchupRequest = MutableStateFlow<CatchupRequest?>(null)

    val epgMapState: StateFlow<Map<String, List<EpgProgramme>>> = epgByTvgId.asStateFlow()
    val playTickState: StateFlow<Int> = playTick.asStateFlow()
    val sleepTimerEndsAtState: StateFlow<Long?> = sleepTimerEndsAt.asStateFlow()
    val catchupRequestState: StateFlow<CatchupRequest?> = catchupRequest.asStateFlow()

    val totalChannelCount: Int get() = allChannels.value.size

    private val enrichedChannels: StateFlow<List<TvChannel>> = combine(
        allChannels,
        prefs.favoriteIdsFlow,
        epgByTvgId
    ) { channels, favs, epgMap ->
        val now = Instant.now()
        channels.map { ch ->
            val key = ch.tvgId?.trim()?.lowercase()
            val slot = if (!key.isNullOrEmpty() && epgMap.isNotEmpty()) {
                XmlTvParser.extractCurrentAndNext(epgMap[key], now)
            } else {
                null
            }
            ch.copy(
                isFavorite = favs.contains(ch.id),
                currentProgramme = slot?.current ?: ch.currentProgramme,
                nextProgramme = slot?.next ?: ch.nextProgramme
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = combine(allChannels, prefs.favoriteIdsFlow) { channels, favs ->
        val groups = channels.map { it.groupTitle }.distinct()
        buildList {
            add("Всички")
            if (favs.isNotEmpty()) add("Любими")
            addAll(groups)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("Всички"))

    val visibleChannels: StateFlow<List<TvChannel>> = combine(
        enrichedChannels,
        selectedCategory,
        onlyFavorites,
        searchQuery
    ) { channels, category, favOnly, query ->
        channels.filter { ch ->
            val favOk = if (favOnly || category == "Любими") ch.isFavorite else true
            val catOk = if (category == "Всички" || category == "Любими") true else ch.groupTitle == category
            val queryOk = if (query.isBlank()) true else {
                ch.name.contains(query, ignoreCase = true) ||
                    ch.groupTitle.contains(query, ignoreCase = true) ||
                    (ch.currentProgramme?.title?.contains(query, ignoreCase = true) == true)
            }
            favOk && catOk && queryOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeChannel: StateFlow<TvChannel?> = combine(
        enrichedChannels,
        activeChannelId
    ) { channels, activeId ->
        channels.find { it.id == activeId } ?: channels.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedCategoryState: StateFlow<String> = selectedCategory
    val onlyFavoritesState: StateFlow<Boolean> = onlyFavorites
    val searchQueryState: StateFlow<String> = searchQuery
    val isLoadingState: StateFlow<Boolean> = isLoading
    val alertTextState: StateFlow<String?> = alertText

    init {
        viewModelScope.launch {
            epgByTvgId.value = epgCacheStore.readCache()
            val cachedM3u = prefs.cachedM3uFlow.first()
            val initialM3u = cachedM3u ?: M3uParser.demoPlaylist()
            applyPlaylist(
                rawM3u = initialM3u,
                title = if (cachedM3u != null) "Запазен плейлист" else "EON Демо",
                selectFirst = true
            )
            if (!prefs.externalEpgUrlFlow.first().isNullOrBlank()) {
                EpgRefreshWorker.scheduleEverySixHours(getApplication())
            }
        }
    }

    fun reloadEpgFromCache() {
        viewModelScope.launch {
            val cached = epgCacheStore.readCache()
            if (cached.isNotEmpty()) {
                epgByTvgId.value = cached
            }
        }
    }

    fun selectChannel(channel: TvChannel) {
        catchupRequest.value = null
        activeChannelId.value = channel.id
        playTick.value += 1
    }

    fun selectChannelByNumber(number: Int): Boolean {
        val target = allChannels.value.firstOrNull { it.number == number } ?: return false
        selectChannel(target)
        return true
    }

    fun stepChannel(delta: Int) {
        val list = visibleChannels.value
        if (list.isEmpty()) return
        val currentIdx = list.indexOfFirst { it.id == activeChannel.value?.id }.coerceAtLeast(0)
        val targetIdx = (currentIdx + delta + list.size) % list.size
        selectChannel(list[targetIdx])
    }

    fun resumeLive() {
        catchupRequest.value = null
        playTick.value += 1
    }

    fun requestCatchup(channel: TvChannel, programme: EpgProgramme) {
        val url = CatchupHelper.buildUrl(channel, programme.startTime, programme.endTime, Instant.now())
        if (url == null) {
            alertText.value = "Архивът за „${programme.title}“ не е наличен (каналът не поддържа catch-up или периодът е изтекъл)."
            return
        }
        catchupRequest.value = CatchupRequest(
            channel = channel,
            streamUrl = url,
            programmeTitle = programme.title
        )
    }

    fun consumeCatchup() {
        catchupRequest.value = null
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerEndsAt.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun cancelSleepTimer() {
        sleepTimerEndsAt.value = null
    }

    fun selectCategory(category: String) {
        selectedCategory.value = category
        onlyFavorites.value = (category == "Любими")
    }

    fun toggleFavoritesFilter() {
        val next = !onlyFavorites.value
        onlyFavorites.value = next
        selectedCategory.value = if (next) "Любими" else "Всички"
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            prefs.toggleFavorite(channelId)
        }
    }

    fun loadFromUrl(urlText: String) {
        val cleanUrl = urlText.trim()
        if (cleanUrl.isEmpty()) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val content = withContext(Dispatchers.IO) {
                    URL(cleanUrl).openStream().bufferedReader().use { it.readText() }
                }
                applyPlaylist(content, cleanUrl, selectFirst = true)
                prefs.savePlaylist(cleanUrl, content)
                alertText.value = "Плейлистът от URL е зареден успешно."
            } catch (e: Exception) {
                alertText.value = "Неуспешно зареждане от URL: ${e.localizedMessage ?: "грешка в мрежата"}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val content = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Празен или недостъпен файл")
                }
                val fileLabel = uri.lastPathSegment ?: "Локален M3U файл"
                applyPlaylist(content, fileLabel, selectFirst = true)
                prefs.savePlaylist(fileLabel, content)
                alertText.value = "Зареден файл: $fileLabel"
            } catch (e: Exception) {
                alertText.value = "Грешка при отваряне на файл: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadEpgFromUrl(urlText: String) {
        val cleanUrl = urlText.trim()
        if (cleanUrl.isEmpty()) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val map = withContext(Dispatchers.IO) {
                    URL(cleanUrl).openStream().use { raw ->
                        GzipStreamHelper.openPossiblyGzipped(raw, cleanUrl).use { xmlStream ->
                            XmlTvParser.parseProgrammesByChannelId(xmlStream)
                        }
                    }
                }
                epgByTvgId.value = map
                epgCacheStore.writeCache(map)
                prefs.setExternalEpgUrl(cleanUrl)
                prefs.markEpgRefreshed()
                EpgRefreshWorker.scheduleEverySixHours(getApplication(), replaceExisting = true)
                alertText.value = "Заредени EPG данни за ${map.size} канала (автоматично обновяване на 6 ч.)."
            } catch (e: Exception) {
                alertText.value = "Грешка при зареждане на EPG от URL: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadEpgFromUri(uri: Uri) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val fileLabel = uri.lastPathSegment ?: "Локален EPG файл"
                val map = withContext(Dispatchers.IO) {
                    val raw = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Не може да се отвори EPG файлът")
                    raw.use { stream ->
                        GzipStreamHelper.openPossiblyGzipped(stream, fileLabel).use { xmlStream ->
                            XmlTvParser.parseProgrammesByChannelId(xmlStream)
                        }
                    }
                }
                epgByTvgId.value = map
                epgCacheStore.writeCache(map)
                prefs.setExternalEpgUrl(null)
                EpgRefreshWorker.cancelSchedule(getApplication())
                alertText.value = "Зареден EPG файл ($fileLabel) за ${map.size} канала."
            } catch (e: Exception) {
                alertText.value = "Грешка при четене на EPG файл: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun dismissAlert() {
        alertText.value = null
    }

    private suspend fun applyPlaylist(rawM3u: String, title: String, selectFirst: Boolean) {
        val parsed = withContext(Dispatchers.Default) {
            M3uParser.parse(rawM3u)
        }
        allChannels.value = parsed
        catchupRequest.value = null
        if (selectFirst && parsed.isNotEmpty()) {
            activeChannelId.value = parsed.first().id
            playTick.value += 1
        }
        if (parsed.isEmpty()) {
            alertText.value = "Плейлистът ($title) не съдържа валидни потоци."
        }
    }
}
