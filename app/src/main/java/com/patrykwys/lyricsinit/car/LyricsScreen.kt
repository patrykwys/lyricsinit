package com.patrykwys.lyricsinit.car

import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.patrykwys.lyricsinit.CarLayout
import com.patrykwys.lyricsinit.R
import com.patrykwys.lyricsinit.graph
import com.patrykwys.lyricsinit.sync.LyricsView
import com.patrykwys.lyricsinit.sync.message
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Everything the car template is built from, swapped as one immutable value. */
private data class CarState(
    val view: LyricsView = LyricsView.EMPTY,
    val layout: CarLayout = CarLayout.MESSAGE,
    val playing: Boolean = false,
    val thumbnail: Bitmap? = null,
)

/**
 * The only screen on the car display. It re-renders only when the visible line, the layout,
 * play/pause or the thumbnail changes, and always returns the same template type so updates
 * count as refreshes.
 */
class LyricsScreen(carContext: CarContext) : Screen(carContext) {

    private val graph = carContext.graph
    private var state = CarState()

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    graph.engine.view,
                    graph.settings.carLayout,
                    graph.playback.state,
                    graph.artwork.thumbnail,
                ) { view, layout, playback, thumbnail ->
                    CarState(view, layout, playback.isPlaying, thumbnail)
                }
                    .distinctUntilChanged()
                    .collect {
                        state = it
                        invalidate()
                    }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val (view, layout, playing, thumbnail) = state
        val track = view.status.track
        val title = track?.let { "${it.title} · ${it.artist}" } ?: carContext.getString(R.string.app_name)
        val current = view.status.message() ?: view.currentLine?.text.orPlaceholder()
        val next = view.nextLine?.text.orPlaceholder()
        val icon = thumbnail?.let { CarIcon.Builder(IconCompat.createWithBitmap(it)).build() }
        val playPause = action(if (playing) "Pause" else "Play") { graph.playback.playPause() }
        val skip = action("Skip") { graph.playback.next() }

        return when (layout) {
            CarLayout.MESSAGE -> MessageTemplate.Builder(current)
                .setTitle(title)
                .setHeaderAction(Action.APP_ICON)
                .apply { if (icon != null) setIcon(icon) }
                .addAction(playPause)
                .addAction(skip)
                .build()

            CarLayout.PANE -> PaneTemplate.Builder(
                Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setTitle(current)
                            .apply { if (icon != null) setImage(icon, Row.IMAGE_TYPE_LARGE) }
                            .build()
                    )
                    .addRow(Row.Builder().setTitle(next).build())
                    .addAction(playPause)
                    .addAction(skip)
                    .build()
            )
                .setTitle(title)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
    }

    private fun action(title: String, onClick: () -> Unit): Action =
        Action.Builder()
            .setTitle(title)
            .setOnClickListener { onClick() }
            .build()

    private fun String?.orPlaceholder(): String = if (isNullOrBlank()) PLACEHOLDER else this

    private companion object {
        const val PLACEHOLDER = "♪"
    }
}
