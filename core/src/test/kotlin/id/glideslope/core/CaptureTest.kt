package id.glideslope.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptureTest {
    private val ovo = "ovo.id"
    private fun raw(
        title: String? = "Pembayaran Berhasil",
        bigText: String? = null,
        lines: List<String> = emptyList(),
        text: String? = "Kamu membayar Rp25.000 ke Toko Uji",
        pkg: String = ovo,
        summary: Boolean = false,
        ongoing: Boolean = false,
    ) = Raw(pkg, "0|ovo.id|7|null|10001", 1_793_000_000_000, title, bigText, lines, text, summary, ongoing)

    @Test fun titleThenTextWithIsoTime() {
        val p = capture(raw(), setOf(ovo))!!
        assertEquals("Pembayaran Berhasil\nKamu membayar Rp25.000 ke Toko Uji", p.text)
        assertEquals("2026-10-26T07:33:20Z", p.postedAt)
        assertEquals(ovo, p.sourceApp)
    }

    @Test fun prefersBigTextThenLines() {
        assertEquals("T\nPanjang", capture(raw(title = "T", bigText = "Panjang", lines = listOf("a")), setOf(ovo))!!.text)
        assertEquals("T\na\nb", capture(raw(title = "T", lines = listOf("a", " ", "b")), setOf(ovo))!!.text)
    }

    @Test fun skipsOtherAppsSummariesOngoingAndEmpty() {
        assertNull(capture(raw(pkg = "com.whatsapp"), setOf(ovo)))
        assertNull(capture(raw(summary = true), setOf(ovo)))
        assertNull(capture(raw(ongoing = true), setOf(ovo)))
        assertNull(capture(raw(title = " ", text = null), setOf(ovo)))
    }

    @Test fun sameTitleAndTextOnceAndCapped() {
        assertEquals("Sama", capture(raw(title = "Sama", text = "Sama"), setOf(ovo))!!.text)
        assertEquals(MAX_TEXT, capture(raw(text = "x".repeat(5000)), setOf(ovo))!!.text.length)
    }

    @Test fun dedupeKeyFollowsText() {
        val a = capture(raw(), setOf(ovo))!!
        assertEquals(a.dedupeKey, capture(raw(), setOf(ovo))!!.dedupeKey)
        assertNotEquals(a.dedupeKey, capture(raw(text = "Kamu membayar Rp30.000"), setOf(ovo))!!.dedupeKey)
    }

    @Test fun jsonEscapes() {
        val p = Payload("ovo.id", "a \"b\"\n\\c\u0001", "2026-11-06T00:00:00Z", "k")
        assertEquals("""{"source_app":"ovo.id","text":"a \"b\"\n\\c\u0001","posted_at":"2026-11-06T00:00:00Z"}""", p.toJson())
    }

    @Test fun outcomes() {
        assertEquals(Outcome.SENT, outcome(202))
        assertEquals(Outcome.BAD_SECRET, outcome(401))
        assertEquals(Outcome.DROP, outcome(413))
        assertEquals(Outcome.DROP, outcome(422))
        assertEquals(Outcome.RETRY, outcome(429))
        assertEquals(Outcome.RETRY, outcome(503))
        assertEquals(Outcome.RETRY, outcome(null))
    }
}
