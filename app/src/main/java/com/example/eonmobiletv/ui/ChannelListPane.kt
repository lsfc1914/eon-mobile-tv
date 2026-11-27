package com.example.eonmobiletv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.eonmobiletv.data.CatchupHelper
import com.example.eonmobiletv.model.EpgProgramme
import com.example.eonmobiletv.model.TvChannel
import com.example.eonmobiletv.ui.theme.EonColors

@Composable
fun EonChannelListPane(
    channels: List<TvChannel>,
    categories: List<String>,
    selectedCategory: String,
    onlyFavorites: Boolean,
    searchQuery: String,
    activeChannelId: String?,
    isLoading: Boolean,
    isGuideMode: Boolean,
    epgMap: Map<String, List<EpgProgramme>>,
    onSelectCategory: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleGuideMode: () -> Unit,
    onPlayLive: (TvChannel) -> Unit,
    onPlayCatchup: (TvChannel, EpgProgramme) -> Unit,
    onOpenNumberPad: () -> Unit,
    onOpenM3uUrlDialog: () -> Unit,
    onPickM3uFile: () -> Unit,
    onOpenEpgUrlDialog: () -> Unit,
    onPickEpgFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Търсене на канал или предаване...", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EonColors.Accent) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Изчисти търсенето",
                                tint = EonColors.TextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("channel-search-field")
            )
            IconButton(onClick = onOpenNumberPad) {
                Icon(
                    Icons.Default.Dialpad,
                    contentDescription = "Номер на канал",
                    tint = EonColors.Accent,
                    modifier = Modifier.testTag("channel-number-pad-btn")
                )
            }
            IconButton(onClick = onToggleFavorites) {
                Icon(
                    imageVector = if (onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Любими",
                    tint = if (onlyFavorites) EonColors.Favorite else Color.White,
                    modifier = Modifier.testTag("favorites-filter-btn")
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            FilterChip(
                selected = !isGuideMode,
                onClick = { if (isGuideMode) onToggleGuideMode() },
                label = { Text("Списък", maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EonColors.Accent,
                    selectedLabelColor = EonColors.OnAccent,
                    containerColor = EonColors.KeypadKey,
                    labelColor = EonColors.TextSoft
                ),
                modifier = Modifier.testTag("mode-channels-btn")
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = isGuideMode,
                onClick = { if (!isGuideMode) onToggleGuideMode() },
                label = { Text("ТВ Програма", maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EonColors.Accent,
                    selectedLabelColor = EonColors.OnAccent,
                    containerColor = EonColors.KeypadKey,
                    labelColor = EonColors.TextSoft
                ),
                modifier = Modifier.testTag("mode-guide-btn")
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenM3uUrlDialog, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Link, contentDescription = "M3U URL", tint = EonColors.TextMuted)
            }
            IconButton(onClick = onPickM3uFile, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Локален M3U", tint = EonColors.TextMuted)
            }
            IconButton(onClick = onOpenEpgUrlDialog, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "EPG URL", tint = EonColors.TextMuted)
            }
            IconButton(onClick = onPickEpgFile, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.UploadFile, contentDescription = "Локален EPG", tint = EonColors.TextMuted)
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                color = EonColors.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (isGuideMode) {
            EonGuidePane(
                channels = channels,
                epgMap = epgMap,
                onPlayLive = onPlayLive,
                onPlayCatchup = onPlayCatchup,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("category-chips-row")
            ) {
                items(categories, key = { it }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EonColors.Accent.copy(alpha = 0.2f),
                            selectedLabelColor = EonColors.Accent,
                            containerColor = EonColors.KeypadKey.copy(alpha = 0.5f),
                            labelColor = EonColors.TextSoft
                        )
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("channel-list")
            ) {
                items(channels, key = { it.id }) { channel ->
                    val selected = channel.id == activeChannelId
                    ChannelCard(
                        channel = channel,
                        selected = selected,
                        onSelectChannel = onSelectChannel,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: TvChannel,
    selected: Boolean,
    onSelectChannel: (TvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val offsetAnim by animateDpAsState(
        targetValue = if (selected) 4.dp else 0.dp,
        label = "cardOffset"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) EonColors.CardActive else EonColors.Card
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(1.dp, EonColors.Accent.copy(alpha = 0.65f))
        } else {
            BorderStroke(1.dp, EonColors.Border)
        },
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = offsetAnim)
            .clickable { onSelectChannel(channel) }
            .testTag("channel-item-${channel.number}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EonColors.KeypadKey)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${channel.number}",
                    color = EonColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EonColors.Page)
                    .padding(4.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        color = if (selected) EonColors.Accent else Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = channel.groupTitle,
                        color = EonColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                channel.currentProgramme?.let { cur ->
                    Text(
                        text = "Сега: ${cur.title}",
                        color = Color(0xFFD6E2EC),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                channel.currentProgramme?.let { cur ->
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { cur.progressFraction() },
                        color = EonColors.Accent,
                        trackColor = EonColors.Border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
                channel.nextProgramme?.let { nxt ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Следва: ${nxt.title}",
                        color = EonColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (CatchupHelper.supportsCatchup(channel)) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Поддържа архив",
                    tint = EonColors.Accent.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 2.dp)
                )
            }
            IconButton(onClick = { onToggleFavorite(channel.id) }) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Любим",
                    tint = if (channel.isFavorite) EonColors.Favorite else EonColors.TextMuted,
                    modifier = Modifier.testTag("favorite-toggle-${channel.number}")
                )
            }
        }
    }
}
