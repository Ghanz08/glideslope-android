// Display helpers ported from the PWA (src/lib/core/money.ts, dates.ts, home.ts) so both clients read the same.
package id.glideslope.core

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
private val MONTHS_LONG = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
private val DAYS = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

/** 5_100_000 → "5,1 jt"; 190_000_000 → "190 jt". */
fun formatJt(n: Long): String {
    val jt = n / 1_000_000.0
    val s = if (abs(jt) >= 100) jt.roundToLong().toString()
    else ((jt * 10).roundToLong() / 10.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.replace('.', ',')
    return "$s jt"
}

fun greeting(hourWib: Int) = when (hourWib) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

/** "Agu 2028" */
fun formatMonthYear(d: LocalDate) = "${MONTHS[d.monthValue - 1]} ${d.year}"

/** "Jumat, 6 November 2026" */
fun formatLongDate(d: LocalDate) = "${DAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS_LONG[d.monthValue - 1]} ${d.year}"

/** "6 Nov" */
fun formatDayMonth(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
