package com.example.eonmobiletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eonmobiletv.model.TvChannel
import com.example.eonmobiletv.player.EonPlayerController
import com.example.eonmobiletv.player.TrackOption
import com.example.eonmobiletv.ui.theme.EonColors

@Composable
fun EonUrlInputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EonColors.Card,
        title = { Text(title, color = EonColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text(placeholder, maxLines = 1) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url-input-field")
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(urlInput) },
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EonColors.Accent,
                    contentColor = EonColors.OnAccent
                )
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отказ", color = EonColors.TextMuted)
            }
        }
    )
}

@Composable
fun ChannelNumberPadDialog(
    channels: List<TvChannel>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val match = typed.toIntOrNull()?.let { n -> channels.firstOrNull { it.number == n } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EonColors.Card,
        title = { Text("Въведете номер на канал", color = EonColors.TextPrimary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(EonColors.Page)
                        .border(1.dp, EonColors.Border, RoundedCornerShape(14.dp))
                        .padding(vertical = 12.dp)
                        .testTag("number-pad-display"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = typed.ifEmpty { "•" },
                            color = EonColors.Accent,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                typed.isEmpty() -> "Въведете цифри"
                                match != null -> "${match.number}. ${match.name}"
                                else -> "Няма канал с този номер"
                            },
                            color = if (match != null) EonColors.TextPrimary else EonColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val keyRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "OK")
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keyRows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key -> KeypadKey(label = key, onClick = {
                                when (key) {
                                    "C" -> typed = ""
                                    "OK" -> typed.toIntOrNull()?.let(onSelect)
                                    else -> if (typed.length < 3) typed += key
                                }
                            }) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Затвори", color = EonColors.TextMuted)
            }
        }
    )
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EonColors.KeypadKey)
            .clickable(onClick = onClick)
            .testTag("numeric-pad-key-$label"),
        contentAlignment = Alignment.Center
    ) {
        if (label == "OK") {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Избери канал",
                tint = EonColors.Accent,
                modifier = Modifier.size(26.dp)
            )
        } else {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EonSleepTimerDialog(
    activeMinutes: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 15, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EonColors.Card,
        title = { Text("Таймер за заспиване", color = EonColors.TextPrimary) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(options.size) { idx ->
                    val minutes = options[idx]
                    val isSelected = minutes == (activeMinutes ?: 0)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(minutes) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("sleep-timer-option-$minutes")
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = EonColors.Accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sleepLabel(minutes),
                            color = if (isSelected) EonColors.Accent else EonColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Затвори", color = EonColors.TextMuted)
            }
        }
    )
}

private fun sleepLabel(minutes: Int): String = when (minutes) {
    0 -> "Изключен"
    15 -> "15 минути"
    30 -> "30 минути"
    45 -> "45 минути"
    60 -> "1 час"
    90 -> "1 час и 30 мин"
    120 -> "2 часа"
    else -> "$minutes минути"
}

@Composable
fun EonTracksDialog(
    audioOptions: List<TrackOption>,
    textOptions: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectText: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EonColors.Card,
        title = { Text("Аудио и Субтитри", color = EonColors.TextPrimary) },
        text = {
            if (audioOptions.isEmpty() && textOptions.isEmpty()) {
                Text(
                    text = "Няма налични алтернативни аудио или субтитри потоци за този канал.",
                    color = EonColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (audioOptions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Аудио поток",
                                color = EonColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(audioOptions.size) { i ->
                            val option = audioOptions[i]
                            TrackRow(
                                name = option.name,
                                detail = option.detail,
                                isSelected = option.isSelected,
                                tag = "audio-track-$i",
                                onClick = { onSelectAudio(option) }
                            )
                        }
                    }
                    if (textOptions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Субтитри",
                                color = EonColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        item {
                            TrackRow(
                                name = "Изключени",
                                detail = null,
                                isSelected = textOptions.none { it.isSelected },
                                tag = "subtitles-off",
                                onClick = onDisableSubtitles
                            )
                        }
                        items(textOptions.size) { i ->
                            val option = textOptions[i]
                            TrackRow(
                                name = option.name,
                                detail = option.detail,
                                isSelected = option.isSelected,
                                tag = "subtitle-track-$i",
                                onClick = { onSelectText(option) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Затвори", color = EonColors.TextMuted)
            }
        }
    )
}

@Composable
private fun TrackRow(
    name: String,
    detail: String?,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) EonColors.CardActive else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) EonColors.Accent.copy(alpha = 0.55f) else EonColors.Border,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) EonColors.Accent else EonColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (isSelected) EonColors.Accent else EonColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = EonColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
