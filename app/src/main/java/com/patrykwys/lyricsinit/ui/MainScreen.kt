package com.patrykwys.lyricsinit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykwys.lyricsinit.AppGraph
import com.patrykwys.lyricsinit.CarLayout
import com.patrykwys.lyricsinit.lyrics.LyricLine
import com.patrykwys.lyricsinit.lyrics.Lyrics
import com.patrykwys.lyricsinit.media.Track
import com.patrykwys.lyricsinit.sync.LyricsStatus
import com.patrykwys.lyricsinit.sync.LyricsView
import com.patrykwys.lyricsinit.sync.message
import kotlinx.coroutines.launch
import java.util.Locale

private const val OFFSET_STEP_MS = 250L

@Composable
fun MainScreen(
    graph: AppGraph,
    hasAccess: Boolean,
    batteryUnrestricted: Boolean,
    onGrantAccess: () -> Unit,
    onFixBattery: () -> Unit,
) {
    val view by graph.engine.view.collectAsStateWithLifecycle()
    val offsetMs by graph.settings.offsetMs.collectAsStateWithLifecycle(initialValue = 0L)
    val carLayout by graph.settings.carLayout.collectAsStateWithLifecycle(initialValue = CarLayout.MESSAGE)
    val scope = rememberCoroutineScope()

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
            NowPlaying(view.status.track)
            OffsetControls(
                offsetMs = offsetMs,
                onAdjust = { delta -> scope.launch { graph.settings.adjustOffset(delta) } },
                onReset = { scope.launch { graph.settings.resetOffset() } },
            )
            CarLayoutPicker(
                selected = carLayout,
                onSelect = { layout -> scope.launch { graph.settings.setCarLayout(layout) } },
            )
            HorizontalDivider()
            LyricsBody(view, Modifier.weight(1f).fillMaxWidth())
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
private fun NowPlaying(track: Track?) {
    Column {
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

@Composable
private fun LyricsBody(view: LyricsView, modifier: Modifier) {
    val status = view.status
    val lines = view.syncedLines
    when {
        lines != null -> SyncedLyrics(lines, view.index, modifier)

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
private fun SyncedLyrics(lines: List<LyricLine>, index: Int, modifier: Modifier) {
    val listState = rememberLazyListState()
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
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    active -> MaterialTheme.colorScheme.primary
                    i < index -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
