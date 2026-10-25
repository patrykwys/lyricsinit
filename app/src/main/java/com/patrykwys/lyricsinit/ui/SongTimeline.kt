package com.patrykwys.lyricsinit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.patrykwys.lyricsinit.media.PlaybackSnapshot
import java.util.Locale

private val TrackHeight = 5.dp
private val ThumbRadius = 8.dp
private val ThumbHeldRadius = 11.dp

/**
 * Seek bar for the current song. Spotify reports its position rarely, so the thumb is
 * extrapolated from [playback] every frame and glides instead of jumping.
 */
@Composable
fun SongTimeline(
    durationMs: Long,
    playback: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = rememberLivePosition(playback)
    val latestPlayback by rememberUpdatedState(playback)
    val latestOnSeek by rememberUpdatedState(onSeek)
    // Under the finger while the thumb is held.
    var heldMs by remember { mutableStateOf<Long?>(null) }
    // A seek Spotify hasn't confirmed yet, so the thumb doesn't snap back after letting go.
    var pendingSeek by remember { mutableStateOf<PendingSeek?>(null) }
    val shownMs = remember {
        {
            heldMs
                ?: pendingSeek?.takeIf { it.issuedAgainst == latestPlayback }?.targetMs
                ?: live.value
        }
    }
    val elapsedSeconds by remember { derivedStateOf { shownMs() / 1000 } }
    val thumbRadius by animateDpAsState(if (heldMs != null) ThumbHeldRadius else ThumbRadius, label = "thumb")

    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(ThumbHeldRadius * 2)
                .pointerInput(durationMs) {
                    val inset = ThumbHeldRadius.toPx()
                    fun msAt(x: Float): Long =
                        ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f).times(durationMs).toLong()
                    // A tap seeks too: the thumb jumps under the finger on touch and the seek is sent on release.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        heldMs = msAt(down.position.x)
                        val released = horizontalDrag(down.id) { change ->
                            heldMs = msAt(change.position.x)
                            change.consume()
                        }
                        val target = heldMs
                        heldMs = null
                        if (released && target != null) {
                            pendingSeek = PendingSeek(target, latestPlayback)
                            latestOnSeek(target)
                        }
                    }
                },
        ) {
            val inset = ThumbHeldRadius.toPx()
            val trackY = size.height - inset
            val fraction = (shownMs().toFloat() / durationMs).coerceIn(0f, 1f)
            val thumb = Offset(inset + fraction * (size.width - 2 * inset), trackY)
            val stroke = TrackHeight.toPx()
            drawLine(rest, Offset(inset, trackY), Offset(size.width - inset, trackY), stroke, StrokeCap.Round)
            drawLine(played, Offset(inset, trackY), thumb, stroke, StrokeCap.Round)

            val radius = thumbRadius.toPx()
            drawCircle(played.copy(alpha = 0.25f), radius * 1.6f, thumb)
            drawCircle(Color.White, radius, thumb)
            drawCircle(played, radius - 2.5.dp.toPx(), thumb)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = ThumbHeldRadius - 4.dp)) {
            TimeLabel(elapsedSeconds)
            Spacer(Modifier.weight(1f))
            TimeLabel(durationMs / 1000)
        }
    }
}

private data class PendingSeek(val targetMs: Long, val issuedAgainst: PlaybackSnapshot)

/** Position recomputed on every frame while playing; reading it only in a draw block redraws without recomposing. */
@Composable
private fun rememberLivePosition(playback: PlaybackSnapshot): State<Long> =
    produceState(playback.positionNow(), playback) {
        value = playback.positionNow()
        while (playback.isPlaying) withFrameMillis { value = playback.positionNow() }
    }

@Composable
private fun TimeLabel(seconds: Long) {
    Text(
        text = formatTrackTime(seconds),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 125 → "2:05". */
internal fun formatTrackTime(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
