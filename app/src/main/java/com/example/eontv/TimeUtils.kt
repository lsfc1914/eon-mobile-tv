package com.example.eontv

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val bgTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Sofia"))

fun formatEventTimeRange(startMillis: Long, endMillis: Long): String {
    val start = bgTimeFormatter.format(Instant.ofEpochMilli(startMillis))
    val end = bgTimeFormatter.format(Instant.ofEpochMilli(endMillis))
    return "$start - $end"
}
