package id.glideslope.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RunwayChartTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun d(m: Int, day: Int, y: Int = 2027) = LocalDate.of(y, m, day)
    private val step = ChartStep(d(2, 14), "Biaya semester", 9_000_000)

    @Test fun `a planned step keeps the point after it and names the step`() {
        val series = listOf(ChartPoint(today, 100_000_000), ChartPoint(d(2, 14), 100_000_000), ChartPoint(d(2, 14), 91_000_000), ChartPoint(d(3, 14), 85_000_000))
        val pts = scrubPoints(series, emptyList(), today, listOf(step))
        assertEquals(3, pts.size)
        assertEquals(91_000_000, pts[1].value)
        assertEquals("Biaya semester", pts[1].step?.label)
        assertEquals(ScrubKind.Sekarang, pts[0].kind)
        assertEquals(ScrubKind.Proyeksi, pts[2].kind)
    }

    @Test fun `history comes first and is labelled Riwayat`() {
        val past = listOf(ChartPoint(d(10, 1, 2026), 120_000_000), ChartPoint(d(11, 1, 2026), 110_000_000))
        val pts = scrubPoints(listOf(ChartPoint(today, 100_000_000)), past, today, emptyList())
        assertEquals(listOf(ScrubKind.Riwayat, ScrubKind.Riwayat, ScrubKind.Sekarang), pts.map { it.kind })
    }

    @Test fun `the nearest point to the finger wins`() {
        val series = listOf(ChartPoint(today, 1), ChartPoint(d(2, 14), 1), ChartPoint(d(8, 14), 1))
        val pts = scrubPoints(series, emptyList(), today, emptyList())
        val xOf = { date: LocalDate -> java.time.temporal.ChronoUnit.DAYS.between(today, date).toFloat() }
        assertEquals(0, nearestScrub(pts, -50f, xOf))
        assertEquals(1, nearestScrub(pts, 80f, xOf))
        assertEquals(2, nearestScrub(pts, 9999f, xOf))
    }

    @Test fun `the reading names date kind money and step, and hides money in privacy mode`() {
        val p = ScrubPoint(d(2, 14), 195_000_000, ScrubKind.Proyeksi, step)
        assertEquals("14 Feb 2027 · Proyeksi · Rp 195 jt · Biaya semester −9 jt", readout(p, hidden = false))
        assertEquals("14 Feb 2027 · Proyeksi · Rp •• · Biaya semester −Rp ••", readout(p, hidden = true))
        assertNull(ScrubPoint(today, 1, ScrubKind.Sekarang, null).step)
    }
}
