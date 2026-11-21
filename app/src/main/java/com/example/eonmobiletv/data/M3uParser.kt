package com.example.eonmobiletv.data

import com.example.eonmobiletv.model.EpgProgramme
import com.example.eonmobiletv.model.TvChannel
import java.time.Instant
import java.time.temporal.ChronoUnit

object M3uParser {

    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(content: String): List<TvChannel> {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val channels = mutableListOf<TvChannel>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String = "Основни"
        var pendingTvgId: String? = null
        var number = 1

        for (line in lines) {
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val attrs = attrRegex.findAll(line)
                    .associate { it.groupValues[1].lowercase() to it.groupValues[2] }

                pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Основни"
                pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }

                val commaIdx = line.lastIndexOf(',')
                pendingName = if (commaIdx != -1 && commaIdx + 1 < line.length) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    "Канал $number"
                }
            } else if (!line.startsWith("#")) {
                val name = pendingName ?: "Канал $number"
                val uniqueId = "${number}_${name.hashCode()}_${line.hashCode()}"
                val (nowProg, nextProg) = buildEpgForChannel(name, pendingGroup, number)

                channels.add(
                    TvChannel(
                        id = uniqueId,
                        number = number,
                        name = name,
                        streamUrl = line,
                        logoUrl = pendingLogo,
                        groupTitle = pendingGroup,
                        tvgId = pendingTvgId,
                        currentProgramme = nowProg,
                        nextProgramme = nextProg
                    )
                )
                number++
                pendingName = null
                pendingLogo = null
                pendingGroup = "Основни"
                pendingTvgId = null
            }
        }
        return channels
    }

    private fun buildEpgForChannel(
        channelName: String,
        groupTitle: String,
        index: Int
    ): Pair<EpgProgramme, EpgProgramme> {
        val now = Instant.now()
        val startNow = now.minus(22, ChronoUnit.MINUTES)
        val endNow = now.plus(38, ChronoUnit.MINUTES)
        val endNext = endNow.plus(55, ChronoUnit.MINUTES)

        val current = EpgProgramme(
            title = when (index % 4) {
                0 -> "Новини на фокус — пряко включване"
                1 -> "EON Спорт: Студио и анализи"
                2 -> "Кино вечер: Премиера в HD"
                else -> "Познание и технологии — $channelName"
            },
            startTime = startNow,
            endTime = endNow,
            categoryTag = groupTitle,
            description = "Излъчване на живо • $channelName"
        )

        val next = EpgProgramme(
            title = when (index % 4) {
                0 -> "Актуално от деня и времето"
                1 -> "Пряко предаване от стадиона"
                2 -> "Игрален филм (Full HD)"
                else -> "Светът около нас"
            },
            startTime = endNow,
            endTime = endNext,
            categoryTag = groupTitle
        )
        return current to next
    }

    fun demoPlaylist(): String = """
        #EXTM3U
        #EXTINF:-1 tvg-id="eon.hls.1" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/a/a7/React-icon.svg/240px-React-icon.svg.png" group-title="Спорт & Live",EON Live HLS (Adaptive)
        https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
        #EXTINF:-1 tvg-id="eon.dash.1" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/6/61/HTML5_logo_and_wordmark.svg/240px-HTML5_logo_and_wordmark.svg.png" group-title="Филми & Сериали",EON Cinema DASH
        https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd
        #EXTINF:-1 tvg-id="eon.mp4.1" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Python-logo-notext.svg/240px-Python-logo-notext.svg.png" group-title="Документални",EON Docu MP4
        https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4
        #EXTINF:-1 tvg-id="eon.mp4.2" group-title="Забавление",EON Fun MP4
        https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4
    """.trimIndent()
}
