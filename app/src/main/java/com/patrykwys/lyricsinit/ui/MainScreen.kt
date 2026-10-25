package com.patrykwys.lyricsinit.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrykwys.lyricsinit.CarLayout
import com.patrykwys.lyricsinit.lyrics.LyricLine
import com.patrykwys.lyricsinit.lyrics.Lyrics
import com.patrykwys.lyricsinit.media.Track
import com.patrykwys.lyricsinit.sync.LyricsStatus
import com.patrykwys.lyricsinit.sync.LyricsView
import com.patrykwys.lyricsinit.sync.message
import java.util.Locale

private const val OFFSET_STEP_MS = 250L

/** Stateless: draws [state] and reports user actions; [MainViewModel] owns the data. */
@Composable
fun MainScreen(
    state: MainUiState,
    hasAccess: Boolean,
    batteryUnrestricted: Boolean,
    onGrantAccess: () -> Unit,
    onFixBattery: () -> Unit,
    onSeek: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSelectCarLayout: (CarLayout) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasAccess) {
                SetupCard(
                    title = "Notification access needed",
                    body = "This is how the app sees what Spotify is playing. Turn on \"Lyrics\" in the list.",
                    action = "Grant access",
                    onClick = onGrantAccess,
                )
            }
            if (!batteryUnrestricted) {
                SetupCard(
                    title = "Battery optimisation is on",
                    body = "One UI may stop the app in the background and the car screen will freeze.",
                    action = "Allow background",
                    onClick = onFixBattery,
                )
            }
            val track = state.view.status.track
            NowPlaying(track, state.albumThumbnail, state.artistThumbnail)
            // Spotify leaves the duration out for some streams; there is nothing to scale the bar to then.
            if (track != null && track.durationMs > 0) {
                SongTimeline(track.durationMs, state.playback, waveSeed = track.hashCode(), onSeek = onSeek)
            }
            OffsetControls(state.offsetMs, onAdjust = onAdjustOffset, onReset = onResetOffset)
            CarLayoutPicker(state.carLayout, onSelect = onSelectCarLayout)
            HorizontalDivider()
            if (state.view.voices.isNotEmpty()) SingerLegend(state.view.voices)
            LyricsBody(state.view, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun NowPlaying(track: Track?, album: Bitmap?, artist: Bitmap?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Thumbnail(album, contentDescription = "Album", shape = RoundedCornerShape(8.dp))
        Thumbnail(artist, contentDescription = "Artist", shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Text(
                text = track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artist ?: "Start a song in Spotify",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Thumbnail(bitmap: Bitmap?, contentDescription: String, shape: Shape) {
    val clipped = Modifier
        .size(56.dp)
        .clip(shape)
    if (bitmap == null) {
        Box(clipped.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("♪", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(image, contentDescription = contentDescription, modifier = clipped, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun OffsetControls(offsetMs: Long, onAdjust: (Long) -> Unit, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Offset ${String.format(Locale.US, "%+.2f s", offsetMs / 1000.0)}")
            Text(
                text = "+ shows lyrics earlier",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { onAdjust(-OFFSET_STEP_MS) }) { Text("−") }
        OutlinedButton(onClick = { onAdjust(OFFSET_STEP_MS) }) { Text("+") }
        TextButton(onClick = onReset) { Text("Reset") }
    }
}

@Composable
private fun CarLayoutPicker(selected: CarLayout, onSelect: (CarLayout) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Car screen", Modifier.weight(1f))
        CarLayout.entries.forEach { layout ->
            FilterChip(
                selected = layout == selected,
                onClick = { onSelect(layout) },
                label = { Text(layout.label) },
            )
        }
    }
}

/** Which color is whom, e.g. "● Eminem  ● Rihanna". */
@Composable
private fun SingerLegend(voices: List<String>) {
    val palette = singerPalette()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        voices.forEachIndexed { i, singer ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette[i % palette.size])
                )
                Text(singer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LyricsBody(view: LyricsView, modifier: Modifier) {
    val status = view.status
    val lines = view.syncedLines
    when {
        lines != null -> SyncedLyrics(lines, view.index, view.voices, modifier)

        status is LyricsStatus.Ready && status.lyrics is Lyrics.Plain -> Text(
            text = status.lyrics.text,
            modifier = modifier.verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyLarge,
        )

        else -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = status.message().orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, index: Int, voices: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    val palette = singerPalette()
    // Keep the current line a couple of rows from the top so upcoming lines stay visible.
    LaunchedEffect(lines, index) {
        listState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == index
            val singerColors = palette.forSingers(line.singers, voices)
            val style = MaterialTheme.typography.headlineSmall
            // Singer colors replace the primary highlight, so the current line stands out by weight and opacity.
            val alpha = when {
                active -> 1f
                i < index -> 0.45f
                else -> 0.75f
            }
            Text(
                text = line.text.ifBlank { "♪" },
                // A shared line blends its singers' colors.
                style = if (singerColors.size > 1) {
                    style.copy(brush = Brush.linearGradient(singerColors), alpha = alpha)
                } else {
                    style
                },
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    singerColors.size > 1 -> Color.Unspecified
                    singerColors.size == 1 -> singerColors.single().copy(alpha = alpha)
                    active -> MaterialTheme.colorScheme.primary
                    i < index -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
