package com.example.eonmobiletv.model

import java.time.Instant

data class EpgProgramme(
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val categoryTag: String = "LIVE",
    val description: String = ""
) {
    fun progressFraction(now: Instant = Instant.now()): Float {
        val total = (endTime.epochSecond - startTime.epochSecond).coerceAtLeast(1L)
        val elapsed = (now.epochSecond - startTime.epochSecond).coerceIn(0L, total)
        return elapsed.toFloat() / total.toFloat()
    }
}

data class TvChannel(
    val id: String,
    val number: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String = "Български",
    val tvgId: String? = null,
    val isFavorite: Boolean = false,
    val currentProgramme: EpgProgramme? = null,
    val nextProgramme: EpgProgramme? = null
)
