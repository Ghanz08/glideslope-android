// S-04 runway chart on the phone (F-16.22): the same drawing as the web's RunwayChart (history with its area, dashed
// projection, reserve line, planned steps, graduation pill, depletion dot) that can also be read: touch and drag sideways
// (vertical drags still scroll the page), a light tick for each new point, and the reading in the pill's own lane so the
// finger never covers it. TalkBack steps through the points with custom actions.
package id.glideslope.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.core.formatJt
import id.glideslope.core.formatMonthYear
import id.glideslope.core.formatRupiah
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

data class ChartPoint(val date: LocalDate, val value: Long)
data class ChartStep(val date: LocalDate, val label: String, val amount: Long)
enum class ScrubKind(val label: String) { Riwayat("Riwayat"), Sekarang("Sekarang"), Proyeksi("Proyeksi") }
data class ScrubPoint(val date: LocalDate, val value: Long, val kind: ScrubKind, val step: ChartStep?)

/** What can be read: the history, then the projection. A planned step makes two projection points on one date; the one after it is kept. */
fun scrubPoints(series: List<ChartPoint>, past: List<ChartPoint>, today: LocalDate, steps: List<ChartStep>): List<ScrubPoint> =
    past.map { ScrubPoint(it.date, it.value, ScrubKind.Riwayat, null) } +
        series.filterIndexed { i, p -> series.getOrNull(i + 1)?.date != p.date }
            .map { ScrubPoint(it.date, it.value, if (it.date == today) ScrubKind.Sekarang else ScrubKind.Proyeksi, steps.firstOrNull { st -> st.date == it.date }) }

/** The index of the point whose x is closest to [xView]; [xOf] maps a date to the chart's x. */
fun nearestScrub(points: List<ScrubPoint>, xView: Float, xOf: (LocalDate) -> Float): Int {
    var best = 0
    for (i in 1 until points.size) if (abs(xOf(points[i].date) - xView) < abs(xOf(points[best].date) - xView)) best = i
    return best
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
private fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"

/** The reading shown in the pill and read by TalkBack. Money turns into "Rp ••" in privacy mode; date and names stay. */
fun readout(p: ScrubPoint, hidden: Boolean): String {
    val money = if (hidden) "Rp ••" else "Rp ${formatJt(p.value)}"
    val step = p.step?.let { " · ${it.label} −${if (hidden) "Rp ••" else formatJt(it.amount)}" }.orEmpty()
    return "${shortDate(p.date)} · ${p.kind.label} · $money$step"
}

@Composable
fun RunwayChart(
    series: List<ChartPoint>, history: List<ChartPoint>, steps: List<ChartStep>,
    today: LocalDate, graduation: LocalDate, end: LocalDate, leftover: Long, reserve: Long, depleted: Boolean,
) {
    val hidden = LocalPrivacy.current.hidden
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.labelMedium
    val past = history.filter { it.date.isBefore(today) }
    val start = if (past.size >= 2) past.first().date else today
    val span = max(1L, ChronoUnit.DAYS.between(start, end)).toFloat()
    val pts = remember(series, past, today, steps) { scrubPoints(series, past, today, steps) }
    var active by remember(pts) { mutableStateOf<Int?>(null) }
    var lastTick by remember { mutableLongStateOf(0L) }
    val ptsNow by rememberUpdatedState(pts)

    val w = 340f; val h = 220f
    val padL = 4f; val padR = 12f; val padT = 40f; val padB = 8f
    fun xOf(d: LocalDate) = padL + (w - padL - padR) * ChronoUnit.DAYS.between(start, d) / span
    fun select(i: Int?) {
        val next = i?.coerceIn(0, ptsNow.size - 1)
        if (next != null && next != active) {
            val now = System.currentTimeMillis()
            if (now - lastTick >= 40) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); lastTick = now }
        }
        active = next
    }
    val reading = active?.let { pts.getOrNull(it) }
    val alt = "Grafik runway. Dana sekarang ${if (hidden) "disembunyikan" else formatRupiah(series.firstOrNull()?.value ?: 0)}. " +
        "Sisa saat lulus ${formatMonthYear(graduation)} ${if (hidden) "disembunyikan" else formatRupiah(leftover)}."

    Column {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(w / h)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // sideways: read along the line while dragging; anything else is the page scrolling (or a tap)
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        fun at(px: Float) = nearestScrub(ptsNow, px / size.width * w, ::xOf)
                        if (drag != null) {
                            select(at(drag.position.x))
                            horizontalDrag(drag.id) { change -> change.consume(); select(at(change.position.x)) }
                        } else {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                val i = at(down.position.x)
                                select(if (i == active) null else i) // a second tap on the same point closes the reading
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = alt
                    reading?.let { stateDescription = readout(it, hidden) }
                    customActions = listOf(
                        CustomAccessibilityAction("Titik berikutnya") { select((active ?: -1) + 1); true },
                        CustomAccessibilityAction("Titik sebelumnya") { select((active ?: ptsNow.size) - 1); true },
                        CustomAccessibilityAction("Tutup pembacaan") { select(null); true },
                    )
                },
        ) {
            val k = size.width / w
            fun px(p: Float) = (p / (density * fontScale)).sp
            val maxV = max(max(series.maxOfOrNull { it.value } ?: 1L, past.maxOfOrNull { it.value } ?: 1L), 1L)
            val niceMax = (ceil(maxV / 100_000_000.0) * 100_000_000).toFloat().takeIf { it > 0 } ?: maxV.toFloat()
            fun x(d: LocalDate) = xOf(d) * k
            fun y(v: Long) = (padT + (h - padT - padB) * (1 - v / niceMax)) * k
            fun line(points: List<ChartPoint>) = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.date), y(p.value)) else lineTo(x(p.date), y(p.value)) } }
            val nowValue = series.firstOrNull()?.value ?: 0
            val gx = x(graduation)
            val label11 = base.copy(fontSize = px(11f * k))
            val pillStyle = base.copy(fontSize = px(12f * k), fontWeight = FontWeight(600), color = Token.latar, textAlign = TextAlign.Center)

            drawLine(Token.garis, Offset(padL * k, y(0)), Offset((w - padR) * k, y(0)), strokeWidth = 1.5f * k)
            if (past.size >= 2) {
                val joined = past + ChartPoint(today, nowValue)
                val area = line(joined).apply { lineTo(x(today), y(0)); lineTo(x(start), y(0)); close() }
                drawPath(area, Token.aksenLembut)
                drawPath(line(joined), Token.aksen, style = Stroke(2.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (reserve > 0 && reserve < niceMax) {
                drawLine(Token.peringatan, Offset(padL * k, y(reserve)), Offset((w - padR) * k, y(reserve)), strokeWidth = 1.5f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 4f * k)))
                val t = measurer.measure("Cadangan ${if (hidden) "Rp ••" else "Rp ${formatJt(reserve)}"}", label11.copy(color = Token.peringatan, fontWeight = FontWeight(600)))
                drawText(t, topLeft = Offset((padL + 4f) * k, y(reserve) - 5f * k - t.size.height))
            }
            drawLine(Token.garisKontrol, Offset(x(today), (padT - 6f) * k), Offset(x(today), y(0)), strokeWidth = 1f * k)
            if (series.size > 1) drawPath(line(series), Token.aksen, style = Stroke(2.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * k, 5f * k))))
            steps.filter { it.date.isAfter(today) && it.date.isBefore(end) }.forEachIndexed { i, st ->
                val v = series.lastOrNull { it.date == st.date }?.value ?: 0
                val text = st.label + if (i == 0) " −${if (hidden) "Rp ••" else formatJt(st.amount)}" else ""
                val t = measurer.measure(text, label11.copy(color = Token.teksRedup), maxLines = 1, overflow = TextOverflow.Clip)
                drawText(t, topLeft = Offset(x(st.date) + 4f * k, y(v) - 8f * k - t.size.height))
            }
            drawLine(Token.teks, Offset(gx, (padT - 6f) * k), Offset(gx, y(0)), strokeWidth = 1.5f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 3f * k)))
            drawCircle(Token.permukaan, 7f * k, Offset(x(today), y(nowValue)))
            drawCircle(Token.aksen, 5f * k, Offset(x(today), y(nowValue)))
            drawCircle(Token.permukaan, 8.5f * k, Offset(gx, y(max(0, leftover))))
            drawCircle(Token.aksen, 6f * k, Offset(gx, y(max(0, leftover))), style = Stroke(2.5f * k))
            if (depleted) series.lastOrNull()?.let { drawCircle(Token.galat, 5f * k, Offset(x(it.date), y(it.value))) }

            // the pill: the graduation summary, or the point being read (same lane, wider; text clipped with an ellipsis if it must be)
            val text = reading?.let { readout(it, hidden) } ?: "Wisuda · sisa ${if (hidden) "Rp ••" else "Rp ${formatJt(leftover)}"}"
            val maxW = (w - 8f) * k
            val measured = measurer.measure(text, pillStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (maxW - 28f * k).toInt().coerceAtLeast(1)))
            val pillW = max((if (reading == null) 170f else 140f) * k, measured.size.width + 28f * k).coerceAtMost(maxW)
            val anchor = reading?.let { x(it.date) } ?: gx
            val cx = anchor.coerceIn(pillW / 2 + 4f * k, size.width - pillW / 2 - 4f * k)
            if (reading != null) {
                val ax = x(reading.date)
                drawLine(Token.teksRedup, Offset(ax, (padT - 6f) * k), Offset(ax, y(0)), strokeWidth = 1f * k)
                drawCircle(Token.permukaan, 8f * k, Offset(ax, y(reading.value)))
                drawCircle(Token.aksen, 5.5f * k, Offset(ax, y(reading.value)))
            }
            drawRoundRect(Token.teks, Offset(cx - pillW / 2, (16f - 14f) * k), Size(pillW, 28f * k), CornerRadius(14f * k))
            drawText(measured, topLeft = Offset(cx - measured.size.width / 2f, 16f * k - measured.size.height / 2f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (past.size >= 2) Text(formatMonthYear(start), style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, modifier = Modifier.weight(1f))
            Text("Sekarang", style = MaterialTheme.typography.labelMedium, color = Token.teks, modifier = Modifier.weight(1f), textAlign = if (past.size >= 2) TextAlign.Center else TextAlign.Start)
            Text(formatMonthYear(graduation), style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(formatMonthYear(end), style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}
