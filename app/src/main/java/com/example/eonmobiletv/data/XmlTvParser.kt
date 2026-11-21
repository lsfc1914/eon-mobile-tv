package com.example.eonmobiletv.data

import android.util.Xml
import com.example.eonmobiletv.model.EpgProgramme
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

data class ChannelEpgSlot(
    val current: EpgProgramme?,
    val next: EpgProgramme?
)

object XmlTvParser {

    private val xmltvDateFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyyMMddHHmmss")
        .optionalStart()
        .appendLiteral(' ')
        .optionalStart()
        .appendOffset("+HHMM", "Z")
        .optionalEnd()
        .optionalStart()
        .appendOffset("+HH:MM", "Z")
        .optionalEnd()
        .optionalStart()
        .appendZoneId()
        .optionalEnd()
        .optionalEnd()
        .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
        .toFormatter()

    fun parseProgrammesByChannelId(
        inputStream: InputStream,
        now: Instant = Instant.now()
    ): Map<String, List<EpgProgramme>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, null)
        }

        val result = mutableMapOf<String, MutableList<EpgProgramme>>()
        var eventType = parser.eventType

        var insideProgramme = false
        var channelId: String? = null
        var startInstant: Instant? = null
        var stopInstant: Instant? = null
        var title: String? = null
        var category: String = "EPG"
        var desc: String = ""
        var currentTag: String? = null
        val textBuffer = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name.orEmpty()
                    if (tagName.equals("programme", ignoreCase = true)) {
                        insideProgramme = true
                        channelId = parser.getAttributeValue(null, "channel")?.trim()?.lowercase()
                        startInstant = parseXmltvInstant(parser.getAttributeValue(null, "start"))
                        stopInstant = parseXmltvInstant(parser.getAttributeValue(null, "stop"))
                        title = null
                        category = "EPG"
                        desc = ""
                    } else if (insideProgramme) {
                        currentTag = tagName.lowercase()
                        textBuffer.clear()
                    }
                }

                XmlPullParser.TEXT -> {
                    if (insideProgramme && currentTag != null) {
                        textBuffer.append(parser.text.orEmpty())
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tagName = parser.name.orEmpty()
                    if (insideProgramme) {
                        val content = textBuffer.toString().trim()
                        when (tagName.lowercase()) {
                            "title" -> if (title == null && content.isNotEmpty()) title = content
                            "category" -> if (content.isNotEmpty()) category = content
                            "desc" -> if (content.isNotEmpty()) desc = content
                            "programme" -> {
                                val chId = channelId
                                val start = startInstant
                                val progTitle = title
                                if (!chId.isNullOrEmpty() && start != null && !progTitle.isNullOrEmpty()) {
                                    val end = stopInstant ?: start.plusSeconds(3600)
                                    if (end.isAfter(now.minusSeconds(1800))) {
                                        val prog = EpgProgramme(
                                            title = progTitle,
                                            startTime = start,
                                            endTime = end,
                                            categoryTag = category,
                                            description = desc
                                        )
                                        result.getOrPut(chId) { mutableListOf() }.add(prog)
                                    }
                                }
                                insideProgramme = false
                                channelId = null
                                startInstant = null
                                stopInstant = null
                                currentTag = null
                            }
                        }
                        currentTag = null
                    }
                }
            }
            eventType = parser.next()
        }

        return result.mapValues { (_, list) ->
            list.sortedBy { it.startTime }
        }
    }

    fun extractCurrentAndNext(
        programmes: List<EpgProgramme>?,
        now: Instant = Instant.now()
    ): ChannelEpgSlot? {
        if (programmes.isNullOrEmpty()) return null

        val currentIndex = programmes.indexOfFirst { prog ->
            !now.isBefore(prog.startTime) && now.isBefore(prog.endTime)
        }

        return if (currentIndex != -1) {
            val current = programmes[currentIndex]
            val next = programmes.getOrNull(currentIndex + 1)
            ChannelEpgSlot(current = current, next = next)
        } else {
            val next = programmes.firstOrNull { it.startTime.isAfter(now) }
            ChannelEpgSlot(current = null, next = next)
        }
    }

    private fun parseXmltvInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
        return runCatching {
            val temporal = xmltvDateFormatter.parse(cleaned)
            if (temporal.isSupported(ChronoField.OFFSET_SECONDS)) {
                val offset = temporal.get(ChronoField.OFFSET_SECONDS)
                val ldt = LocalDateTime.from(temporal)
                ldt.toInstant(ZoneOffset.ofTotalSeconds(offset))
            } else {
                LocalDateTime.from(temporal).toInstant(ZoneOffset.UTC)
            }
        }.getOrNull()
    }
}
