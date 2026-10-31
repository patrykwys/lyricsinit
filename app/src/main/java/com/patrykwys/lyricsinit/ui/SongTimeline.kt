package com.patrykwys.lyricsinit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.patrykwys.lyricsinit.media.PlaybackSnapshot
import java.util.Locale

private val TrackHeight = 5.dp
private val ThumbRadius = 8.dp
private val ThumbHeldRadius = 11.dp
private val WaveHeight = 24.dp
private val WaveStep = 2.dp

// The wave sinks into the bar this close to the thumb, so the newest part of it grows out of the bar.
private val WaveTaper = 28.dp
private const val WAVE_RISE_MS = 700

/**
 * Seek bar for the current song. Spotify reports its position rarely, so the thumb is
 * extrapolated from [playback] every frame and glides instead of jumping. While the song plays,
 * a [SongWave] seeded by [waveSeed] rises over the played part; on pause it sinks back into the bar.
 */
@Composable
fun SongTimeline(
    durationMs: Long,
    playback: PlaybackSnapshot,
    waveSeed: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wave = remember(waveSeed) { SongWave(waveSeed) }
    val wavePaths = remember { List(wave.layerCount) { Path() } }
    val waveRise by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1f else 0f,
        animationSpec = tween(WAVE_RISE_MS),
        label = "wave",
    )
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
    val accent = MaterialTheme.colorScheme.tertiary
    // Back to front, translucent so the layers mix where they overlap.
    val waveColors = remember(played, accent) {
        listOf(accent.copy(alpha = 0.45f), played.copy(alpha = 0.5f), lerp(played, Color.White, 0.4f).copy(alpha = 0.55f))
    }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(WaveHeight + ThumbHeldRadius * 2)
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
            val trackWidth = size.width - 2 * inset
            val shown = shownMs()
            val fraction = (shown.toFloat() / durationMs).coerceIn(0f, 1f)
            val thumb = Offset(inset + fraction * trackWidth, trackY)
            drawWave(
                wave, wavePaths, waveColors,
                startX = inset, endX = thumb.x, trackWidth = trackWidth, baseY = trackY,
                height = WaveHeight.toPx() * waveRise, seconds = shown / 1000f,
            )

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

/** Fills each [wave] layer from [startX] to [endX], standing on [baseY]; x maps to the song through [trackWidth]. */
private fun DrawScope.drawWave(
    wave: SongWave,
    paths: List<Path>,
    colors: List<Color>,
    startX: Float,
    endX: Float,
    trackWidth: Float,
    baseY: Float,
    height: Float,
    seconds: Float,
) {
    if (height <= 0f || endX <= startX) return
    val step = WaveStep.toPx()
    val taper = WaveTaper.toPx()
    for (layer in 0 until wave.layerCount) {
        val path = paths[layer].apply {
            reset()
            moveTo(startX, baseY)
        }
        var x = startX
        while (true) {
            val envelope = smoothstep((endX - x) / taper) * smoothstep((x - startX) / (taper / 4))
            path.lineTo(x, baseY - height * envelope * wave.heightAt(layer, (x - startX) / trackWidth, seconds))
            if (x >= endX) break
            x = (x + step).coerceAtMost(endX)
        }
        path.close()
        val color = colors[layer]
        val fade = Brush.verticalGradient(listOf(color.copy(alpha = color.alpha * 0.5f), color), baseY - height, baseY)
        drawPath(path, fade)
    }
}

private fun smoothstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

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
