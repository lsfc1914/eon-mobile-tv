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
