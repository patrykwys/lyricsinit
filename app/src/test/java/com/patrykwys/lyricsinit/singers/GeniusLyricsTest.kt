package com.patrykwys.lyricsinit.singers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeniusLyricsTest {
    private val html = """<div class="rg_embed music"><div class="rg_embed_header">Love the Way You Lie</div>""" +
        """<div class="rg_embed_body"><p>[Chorus: Rihanna]<br><a href="/1">Just gonna stand there</a><br>""" +
        """and watch me burn?</p><p>[Verse 1: Eminem]<br>I can't tell you</p></div></div>"""

    /** Wraps [html] the way Genius's embed.js does: a JS string holding a JSON string holding the HTML. */
    private fun embedScript(html: String): String {
        val json = Json.encodeToString(html).replace("/", "\\/")
        val js = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return "if (document.getElementById('rg_embed_link_532')) {\n" +
            "  document.write('<link rel=\"stylesheet\" href=\"x.css\" />')\n" +
            "  document.write(JSON.parse('$js'))\n}"
    }

    @Test
    fun `reads the lyrics and headers out of the embed script`() {
        assertEquals(
            "[Chorus: Rihanna]\nJust gonna stand there\nand watch me burn?\n\n[Verse 1: Eminem]\nI can't tell you",
            extractEmbedLyrics(embedScript(html)),
        )
    }

    @Test
    fun `an embed without a lyrics body has none`() {
        assertNull(extractEmbedLyrics(embedScript("<div class=\"rg_embed_header\">Nope</div>")))
        assertNull(extractEmbedLyrics("document.write('nothing here')"))
    }
}
