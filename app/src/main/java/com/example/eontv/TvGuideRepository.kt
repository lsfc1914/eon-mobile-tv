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
