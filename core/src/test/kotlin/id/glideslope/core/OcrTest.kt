package id.glideslope.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTest {
    @Test fun `lines become the server's shape`() {
        val j = ocrJson(800, listOf(OcrLine("Nasi \"Goreng\"", 0.0, 20.0, 140.0, 300.0, 164.0), OcrLine("90.000", 0.97, 700.0, 140.0, 780.0, 164.0)))!!
        assertEquals(
            """{"width":800,"lines":[{"text":"Nasi \"Goreng\"","score":0.900,"box":[20.0,140.0,300.0,164.0]},{"text":"90.000","score":0.970,"box":[700.0,140.0,780.0,164.0]}]}""",
            j,
        )
    }

    @Test fun `blank text and inverted boxes are dropped, nothing left means no payload`() {
        assertNull(ocrJson(800, listOf(OcrLine("  ", 1.0, 0.0, 0.0, 1.0, 1.0), OcrLine("x", 1.0, 10.0, 0.0, 5.0, 1.0))))
        assertNull(ocrJson(0, listOf(OcrLine("x", 1.0, 0.0, 0.0, 1.0, 1.0))))
    }

    @Test fun `long text and long lists are capped`() {
        val j = ocrJson(100, List(600) { OcrLine("a".repeat(300), 1.0, 0.0, 0.0, 1.0, 1.0) })!!
        assertEquals(500, Regex("\"text\"").findAll(j).count())
        assertTrue(!j.contains("a".repeat(201)))
    }

    @Test fun `control characters are escaped`() {
        assertTrue(ocrJson(10, listOf(OcrLine("a\tb\u0001", 1.0, 0.0, 0.0, 1.0, 1.0)))!!.contains("a\\tb\\u0001"))
    }

    @Test fun `fitSize shrinks the long side to 1568 and never enlarges`() {
        assertEquals(1568 to 1176, fitSize(4032, 3024))
        assertEquals(800 to 600, fitSize(800, 600))
    }
}
