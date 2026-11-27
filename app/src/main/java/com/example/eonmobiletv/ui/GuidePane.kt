package com.example.eonmobiletv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.eonmobiletv.data.CatchupHelper
import com.example.eonmobiletv.model.EpgProgramme
import com.example.eonmobiletv.model.TvChannel
import com.example.eonmobiletv.ui.theme.EonColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EonGuidePane(
    channels: List<TvChannel>,
    epgMap: Map<String, List<EpgProgramme>>,
    onPlayLive: (TvChannel) -> Unit,
    onPlayCatchup: (TvChannel, EpgProgramme) -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val now = Instant.ofEpochMilli(nowMs)
    var expandedId by remember { mutableStateOf<String?>(null) }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

    if (epgMap.isEmpty()) {
        GuideEmptyState(
            title = "Няма заредена EPG програма",
            subtitle = "Заредете XMLTV / .xml.gz EPG от URL или файл — бутоните с календар в горния ред."
        )
        return
    }

    val guideChannels = channels.filter { ch ->
        epgMap[ch.tvgId?.trim()?.lowercase()].orEmpty().isNotEmpty()
    }

    if (guideChannels.isEmpty()) {
        GuideEmptyState(
            title = "Няма съвпадение по tvg-id",
            subtitle = "EPG данните не съвпадат с tvg-id на каналите в текущия плейлист."
        )
        return
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("guide-list")
    ) {
        items(guideChannels, key = { it.id }) { ch ->
            val programmes = epgMap[ch.tvgId?.trim()?.lowercase()].orEmpty()
            val expanded = expandedId == ch.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EonColors.Card)
                    .border(1.dp, EonColors.Border, RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedId = if (expanded) null else ch.id }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("guide-channel-${ch.number}")
                ) {
                    AsyncImage(
                        model = ch.logoUrl,
                        contentDescription = ch.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EonColors.Page)
                            .padding(3.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${ch.number}. ${ch.name}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${programmes.size} предавания • ${ch.groupTitle}",
                            color = EonColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Свийни програмата" else "Разгъни програмата",
                        tint = EonColors.TextMuted
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        programmes.forEachIndexed { index, programme ->
                            ProgrammeRow(
                                channel = ch,
                                programme = programme,
                                index = index,
                                now = now,
                                timeFmt = timeFmt,
                                onPlayLive = onPlayLive,
                                onPlayCatchup = onPlayCatchup
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeRow(
    channel: TvChannel,
    programme: EpgProgramme,
    index: Int,
    now: Instant,
    timeFmt: DateTimeFormatter,
    onPlayLive: (TvChannel) -> Unit,
    onPlayCatchup: (TvChannel, EpgProgramme) -> Unit
) {
    val isNow = !now.isBefore(programme.startTime) && now.isBefore(programme.endTime)
    val isPast = programme.endTime.isAfter(now).not()
    val canCatchUp = isPast && CatchupHelper.supportsCatchup(channel)

    val rowClickable = isNow || canCatchUp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isNow) EonColors.EpgNowBg else Color.Transparent, RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (isNow) EonColors.Accent.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .then(
                if (rowClickable) {
                    Modifier.clickable {
                        if (isNow) onPlayLive(channel) else onPlayCatchup(channel, programme)
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("guide-programme-${channel.number}-$index")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${timeFmt.format(programme.startTime)}–${timeFmt.format(programme.endTime)}",
                color = if (isNow) EonColors.Accent else EonColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(8.dp))
            ProgrammeBadge(isNow = isNow, isPast = isPast)
            Spacer(modifier = Modifier.weight(1f))
            if (canCatchUp) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onPlayCatchup(channel, programme) }
                        .testTag("epg-catchup-btn-$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Гледай запис",
                        tint = EonColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            text = programme.title,
            color = if (isNow) Color.White else Color(0xFFD6E2EC),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (programme.description.isNotBlank()) {
            Text(
                text = programme.description,
                color = EonColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isNow) {
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { programme.progressFraction(now) },
                color = EonColors.Accent,
                trackColor = EonColors.Border,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }
    }
}

@Composable
private fun ProgrammeBadge(isNow: Boolean, isPast: Boolean) {
    val (text, bg, fg) = when {
        isNow -> Triple("НА ЖИВО", EonColors.Live, Color.White)
        isPast -> Triple("ИЗЛЪЧЕНО", EonColors.KeypadKey, EonColors.TextMuted)
        else -> Triple("ПРЕДСТОИ", EonColors.EpgCatchupBadge, EonColors.Accent)
    }
    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun GuideEmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = EonColors.TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = EonColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
