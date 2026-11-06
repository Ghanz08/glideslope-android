package id.glideslope.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetsTest {
    @Test fun percentagesAlwaysSumToHundred() {
        val p = percentages(listOf(5_210_000, 60_000_000, 140_000_000, 41_679_000, 40_111_000))
        assertEquals(1000L, p.sumOf { Math.round(it * 10) })
        assertEquals(listOf(1.8, 20.9, 48.8, 14.5, 14.0), p) // same as the PWA's percentages() for these values
        assertEquals(listOf(33.4, 33.3, 33.3), percentages(listOf(1, 1, 1)))
    }

    @Test fun ladderKeepsCashAndSkipsEmptySteps() {
        val (total, rows) = ladderRows(mapOf("cash" to 0L, "gold" to 100L, "deposit" to 300L))
        assertEquals(400L, total)
        assertEquals(listOf("cash", "deposit", "gold"), rows.map { it.step.key })
        assertEquals("75,0%", formatPct(rows[1].pct!!))
    }
}
