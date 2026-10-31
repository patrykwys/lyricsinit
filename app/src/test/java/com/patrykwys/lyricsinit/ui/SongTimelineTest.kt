package com.patrykwys.lyricsinit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTimelineTest {
    @Test
    fun `pads seconds but not minutes`() {
        assertEquals("0:00", formatTrackTime(0))
        assertEquals("2:05", formatTrackTime(125))
        assertEquals("4:34", formatTrackTime(274))
    }

    @Test
    fun `keeps counting minutes past an hour`() {
        assertEquals("61:01", formatTrackTime(3661))
    }
}
