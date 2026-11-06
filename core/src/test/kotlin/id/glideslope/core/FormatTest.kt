package id.glideslope.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test fun jutaLikeThePwa() {
        assertEquals("5,1 jt", formatJt(5_100_000))
        assertEquals("8,4 jt", formatJt(8_350_000))
        assertEquals("92 jt", formatJt(92_000_000))
        assertEquals("190 jt", formatJt(190_400_000))
        assertEquals("0 jt", formatJt(0))
    }

    @Test fun greetingByWibHour() {
        assertEquals("Selamat pagi", greeting(4))
        assertEquals("Selamat siang", greeting(11))
        assertEquals("Selamat sore", greeting(17))
        assertEquals("Selamat malam", greeting(3))
    }

    @Test fun indonesianDates() {
        assertEquals("Jumat, 6 November 2026", formatLongDate(LocalDate.of(2026, 11, 6)))
        assertEquals("Agu 2028", formatMonthYear(LocalDate.of(2028, 8, 31)))
        assertEquals("14 Nov", formatDayMonth(LocalDate.of(2026, 11, 14)))
    }
}
