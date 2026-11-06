package id.glideslope.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun groupsThousandsWithDots() {
        assertEquals("Rp 0", formatRupiah(0))
        assertEquals("Rp 999", formatRupiah(999))
        assertEquals("Rp 1.000", formatRupiah(1000))
        assertEquals("Rp 1.234.567", formatRupiah(1_234_567))
        assertEquals("−Rp 5.000", formatRupiah(-5000))
    }
}
