package com.example.eonmobiletv.data

import com.example.eonmobiletv.model.TvChannel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Изгражда URL за архивно (catch-up) възпроизвеждане на излъчено предаване.
 *
 * Поддържа често срещаните шаблони от IPTV плейлисти:
 *  - ${start}, ${end}, ${finish}, ${begin}, ${timestamp}, ${utc}, ${duration} (епоха в секунди)
 *  - ${start|Y-m-d H:M:S} и подобни форматирани плейсхолдъри
 *  - {utc} / {lutc} (Xtream Codes стил)
 *  - (start) / (end) / (timestamp)
 *  - ако източникът няма плейсхолдър — добавя ?utc=...&lutc=...
 */
object CatchupHelper {

    private val knownPlaceholderRegex = Regex(
        """\$\{(start|end|finish|begin|timestamp|utc|duration)(\|[^}]+)?\}|\{utc\}|\{lutc\}|\((start|end|timestamp)\)"""
    )
    private val formattedPlaceholderRegex = Regex("""\$\{(start|begin|end|finish)\|([^}]+)\}""")

    fun supportsCatchup(channel: TvChannel): Boolean =
        !channel.catchupSource.isNullOrBlank() && channel.catchupDays > 0

    fun buildUrl(channel: TvChannel, start: Instant, end: Instant, now: Instant): String? {
        val source = channel.catchupSource?.trim().orEmpty()
        if (source.isEmpty() || channel.catchupDays <= 0) return null

        val earliest = now.minusSeconds(channel.catchupDays.toLong() * 24 * 3600)
        if (start.isBefore(earliest)) return null

        val startSec = start.epochSecond
        val endSec = end.epochSecond
        val durationSec = (endSec - startSec).coerceAtLeast(0L)

        var url = source
            .replace("\${duration}", durationSec.toString())
            .replace("\${finish}", endSec.toString())
            .replace("\${end}", endSec.toString())
            .replace("\${start}", startSec.toString())
            .replace("\${begin}", startSec.toString())
            .replace("\${timestamp}", startSec.toString())
            .replace("\${utc}", startSec.toString())
            .replace("{utc}", startSec.toString())
            .replace("{lutc}", now.epochSecond.toString())
            .replace("(timestamp)", startSec.toString())
            .replace("(start)", startSec.toString())
            .replace("(end)", endSec.toString())

        url = formattedPlaceholderRegex.replace(url) { match ->
            val which = match.groupValues[1]
            val instant = if (which == "end" || which == "finish") end else start
            val pattern = translateXmltvPattern(match.groupValues[2])
            runCatching {
                DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC).format(instant)
            }.getOrDefault(instant.epochSecond.toString())
        }

        if (!knownPlaceholderRegex.containsMatchIn(source)) {
            val separator = if (source.contains('?')) '&' else '?'
            url = "$source${separator}utc=$startSec&lutc=${now.epochSecond}"
        }
        return url
    }

    /** XMLTV-подобни шаблони (Y-m-d H:M:S) → Java DateTimeFormatter шаблон. */
    private fun translateXmltvPattern(xmltvPattern: String): String = buildString {
        for (ch in xmltvPattern) {
            when (ch) {
                'Y' -> append("yyyy")
                'm' -> append("MM")
                'd' -> append("dd")
                'H' -> append("HH")
                'M' -> append("mm")
                'S' -> append("ss")
                else -> append(ch)
            }
        }
    }
}
