// S-04 Beranda, native: the same sections as the PWA's mobile Beranda, drawn from the local cache.
package id.glideslope.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.data.AccountEntity
import id.glideslope.app.data.Db
import id.glideslope.app.sync.PullWorker
import id.glideslope.core.formatJt
import id.glideslope.core.formatLongDate
import id.glideslope.core.formatMonthYear
import id.glideslope.core.formatRupiah
import id.glideslope.core.greeting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private val WIB = ZoneId.of("Asia/Jakarta")
private val jamFmt = DateTimeFormatter.ofPattern("HH.mm", Locale.forLanguageTag("id")).withZone(WIB)

internal fun JsonObject.long(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
internal fun JsonObject.text(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
internal fun JsonObject.obj(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonObject
internal fun JsonObject.arr(k: String) = this[k]?.takeIf { it !is JsonNull } as? JsonArray

private data class Point(val date: LocalDate, val value: Long)

@Composable
fun HomeScreen(pad: PaddingValues, onOpenInbox: () -> Unit, onOpenAset: () -> Unit, onOpenProfile: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).sync() }
    val summaryJson by dao.metaFlow("summary").collectAsState(initial = null)
    val syncedAt by dao.metaFlow("synced_at").collectAsState(initial = null)
    val syncError by dao.metaFlow("sync_error").collectAsState(initial = null)
    val accounts by dao.activeAccounts().collectAsState(initial = emptyList())
    val reviewCount by dao.needsReviewCount().collectAsState(initial = 0)
    val s = rememberSummary(summaryJson)
    val today = LocalDate.now(WIB)
    val closed = remember { mutableStateListOf<String>() }

    Column(
        Modifier.fillMaxSize().padding(pad).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(greeting(Instant.now().atZone(WIB).hour), formatLongDate(today)) {
            Box(
                Modifier.size(48.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Token.permukaan).clickable(onClick = onOpenProfile)
                    .semantics { contentDescription = "Profil dan pengaturan" },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.user, null, tint = Token.teks, modifier = Modifier.size(22.dp)) }
        }
        syncError?.takeIf { it.isNotBlank() }?.let { InfoBanner(it.substringAfter('|'), warning = true, action = "Coba lagi", onAction = { PullWorker.now(ctx) }) }
        s?.arr("banners")?.map { it.jsonObject }?.filter { it.text("key") !in closed }?.forEach { b ->
            InfoBanner(b.text("text") ?: "", warning = b.text("tone") == "perhatian", onClose = { closed += b.text("key") ?: "" })
        }
        if (s == null) {
            SkeletonCard(4)
            SkeletonCard(2)
        } else {
            Hero(s, today)
            SectionHeader("Akun", "Lihat aset", onOpenAset)
            AccountsRow(accounts, s)
            if (reviewCount > 0) ReviewCard(reviewCount, onOpenInbox)
        }
        syncedAt?.toLongOrNull()?.let {
            Text("Data per ${jamFmt.format(Instant.ofEpochMilli(it))} WIB", style = MaterialTheme.typography.bodySmall, color = Token.teksPelan, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(barClearance()))
    }
}

@Composable
private fun Hero(s: JsonObject, today: LocalDate) {
    val privacy = LocalPrivacy.current
    val runway = s.obj("runway") ?: return
    val status = runway.text("status") ?: "data_belum_cukup"
    val graduation = s.text("graduation_date")?.let(LocalDate::parse)
    val projection = status in setOf("sesuai", "perlu_perhatian", "belum_cukup")
    var view by rememberSaveable { mutableIntStateOf(0) }
    val (pillBg, pillFg, pillIcon) = when (status) {
        "sesuai" -> Triple(Token.aksenLembut, Token.aksenTeks, Ic.circleCheck)
        "perlu_perhatian" -> Triple(Token.peringatanLatar, Token.peringatan, Ic.triangleAlert)
        "belum_cukup" -> Triple(Token.galatLatar, Token.galat, Ic.circleAlert)
        else -> Triple(Token.isian, Token.teksRedup, Ic.info)
    }
    val sentence = when {
        !projection -> null
        runway.long("monthsBeyondGraduation") != null -> "Cukup lebih dari ${runway.long("monthsBeyondGraduation")} bulan setelah lulus"
        runway.text("depletionDate") != null -> "Cukup sampai ±${formatMonthYear(LocalDate.parse(runway.text("depletionDate")))}"
        else -> "Tidak habis dalam proyeksi"
    }
    val plannedCount = runway.long("plannedCountUntilGraduation") ?: 0
    val plannedLabel = s.obj("planned_next")?.text("label")?.lowercase() ?: "pengeluaran terjadwal"

    GsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dana tersisa", style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup)
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { privacy.toggle() }
                    .semantics { contentDescription = if (privacy.hidden) "Tampilkan jumlah" else "Sembunyikan jumlah" },
                contentAlignment = Alignment.Center,
            ) { Icon(if (privacy.hidden) Ic.eyeOff else Ic.eye, null, tint = Token.teksRedup, modifier = Modifier.size(20.dp)) }
        }
        BigAmount(runway.long("funds") ?: 0)
        Spacer(Modifier.height(12.dp))
        Pill(runway.text("label") ?: "", pillIcon, pillBg, pillFg)
        sentence?.let {
            Text(
                it + if (plannedCount > 0) " · termasuk $plannedCount $plannedLabel" else "",
                style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup, modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (projection && graduation != null) {
            Spacer(Modifier.height(20.dp))
            Segmented(listOf("Dana", "Per bulan"), view) { view = it }
            Spacer(Modifier.height(16.dp))
            if (view == 0) {
                val series = runway.arr("series")?.map { Point(LocalDate.parse(it.jsonObject.text("date")), it.jsonObject.long("value") ?: 0) }.orEmpty()
                val end = runway.text("chartEnd")?.let(LocalDate::parse) ?: series.lastOrNull()?.date ?: graduation
                RunwayChart(series, today, graduation, end, runway.long("leftoverAtGraduation") ?: 0, runway.text("depletionDate") != null)
            } else {
                MonthlyBars(s.arr("months")?.map { it.jsonObject }.orEmpty(), s.obj("burn")?.long("burn"))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Token.garis)
        Row {
            Stat("Rata-rata 3 bulan", s.obj("burn")?.long("burn")?.let { if (privacy.hidden) "Rp •••" else "Rp ${formatJt(it)}/bln" } ?: "—")
            Stat("Aman dipakai per hari", runway.long("safePerDay")?.let { rupiah(it) } ?: "—")
        }
    }
}

/** The PWA's mobile RunwayChart geometry (340 × 220 viewBox), drawn natively and scaled to the card width. */
@Composable
private fun RunwayChart(series: List<Point>, today: LocalDate, graduation: LocalDate, end: LocalDate, leftover: Long, depleted: Boolean) {
    val hidden = LocalPrivacy.current.hidden
    val measurer = rememberTextMeasurer()
    val label = "Lulus ${formatMonthYear(graduation)} · sisa ${if (hidden) "Rp ••" else "Rp ${formatJt(leftover)}"}"
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = Token.grafik1)
    val alt = "Grafik runway. Dana sekarang ${if (hidden) "disembunyikan" else formatRupiah(series.firstOrNull()?.value ?: 0)}. " +
        "Sisa saat lulus ${formatMonthYear(graduation)} ${if (hidden) "disembunyikan" else formatRupiah(leftover)}."
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = alt }) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(340f / 220f)) {
            val k = size.width / 340f
            val padL = 4f * k; val padR = 12f * k; val padT = 40f * k; val padB = 8f * k
            val span = max(1L, ChronoUnit.DAYS.between(today, end)).toFloat()
            val maxV = max(series.maxOfOrNull { it.value } ?: 1L, 1L)
            val niceMax = (ceil(maxV / 100_000_000.0) * 100_000_000).toFloat().takeIf { it > 0 } ?: maxV.toFloat()
            fun x(d: LocalDate) = padL + (size.width - padL - padR) * ChronoUnit.DAYS.between(today, d) / span
            fun y(v: Long) = padT + (size.height - padT - padB) * (1 - v / niceMax)
            val before = series.filter { !it.date.isAfter(graduation) }
            val after = series.filter { !it.date.isBefore(graduation) }
            fun line(pts: List<Point>) = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.date), y(p.value)) else lineTo(x(p.date), y(p.value)) } }

            drawLine(Token.garis, Offset(padL, y(0)), Offset(size.width - padR, y(0)), strokeWidth = 1.5f * k)
            if (before.isNotEmpty()) {
                val area = line(before).apply { lineTo(x(graduation), y(0)); lineTo(x(today), y(0)); close() }
                drawPath(area, Token.aksenLembut)
                drawPath(line(before), Token.grafik1, style = Stroke(3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (after.size > 1) drawPath(line(after), Token.grafik1, style = Stroke(3f * k, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * k, 7f * k))))
            val gx = x(graduation)
            drawLine(Token.garisKontrol, Offset(gx, padT - 6f * k), Offset(gx, y(0)), strokeWidth = 1.5f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 4f * k)))
            series.firstOrNull()?.let { drawCircle(Token.grafik1, 6f * k, Offset(x(today), y(it.value))) }
            drawCircle(Token.permukaan, 8f * k, Offset(gx, y(max(0, leftover))))
            drawCircle(Token.grafik1, 6f * k, Offset(gx, y(max(0, leftover))))
            if (depleted) series.lastOrNull()?.let {
                drawCircle(Token.permukaan, 7f * k, Offset(x(it.date), y(it.value)))
                drawCircle(Token.grafik1, 7f * k, Offset(x(it.date), y(it.value)), style = Stroke(3f * k))
            }
            val text = measurer.measure(label, labelStyle)
            val w = text.size.width + 28f * k
            val h = 28f * k
            val cx = gx.coerceIn(w / 2, size.width - w / 2)
            drawRoundRect(Token.aksenLembut, Offset(cx - w / 2, 16f * k - h / 2), Size(w, h), CornerRadius(h / 2))
            drawText(text, topLeft = Offset(cx - text.size.width / 2f, 16f * k - text.size.height / 2f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Sekarang", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, modifier = Modifier.weight(1f))
            Text(formatMonthYear(graduation), style = MaterialTheme.typography.labelMedium, color = Token.teks)
            Text(formatMonthYear(end), style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

@Composable
private fun MonthlyBars(months: List<JsonObject>, average: Long?) {
    val hidden = LocalPrivacy.current.hidden
    val bars = months.dropWhile { (it.long("total") ?: 0) == 0L && it.text("current") != "true" }
    val top = max(max(bars.maxOfOrNull { it.long("total") ?: 0 } ?: 0, average ?: 0), 1).toFloat()
    val desc = bars.joinToString(". ") { b ->
        "${MONTHS[LocalDate.parse(b.text("month")).monthValue - 1]} ${if (hidden) "disembunyikan" else formatRupiah(b.long("total") ?: 0)}"
    }
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Pengeluaran per bulan. $desc" }) {
        Box(Modifier.fillMaxWidth().height(192.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                bars.forEach { b ->
                    val total = b.long("total") ?: 0
                    val oneOff = b.long("oneOff") ?: 0
                    val routine = b.long("routine") ?: 0
                    val current = b["current"]?.jsonPrimitive?.boolean == true
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (hidden) "••" else if (total > 0) formatJt(total) else "Rp 0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Token.teksRedup, maxLines = 1, overflow = TextOverflow.Clip)
                        Spacer(Modifier.height(4.dp))
                        if (oneOff > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(oneOff / top * 0.85f).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(Token.aksenTinggi))
                        if (routine > 0) Box(
                            Modifier.fillMaxWidth().fillMaxHeight(routine / top * 0.85f)
                                .clip(if (oneOff > 0) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                .background(if (current) Token.aksenSedang else Token.aksen),
                        )
                    }
                }
            }
            average?.let { avg ->
                Canvas(Modifier.fillMaxSize()) {
                    val yy = size.height * (1 - avg / top * 0.85f)
                    drawLine(Token.teksRedup, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
            }
        }
        HorizontalDivider(color = Token.garis)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bars.forEach { b ->
                Text(MONTHS[LocalDate.parse(b.text("month")).monthValue - 1], style = MaterialTheme.typography.labelSmall, color = Token.teksRedup, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        average?.let { Text("Garis putus-putus: rata-rata ${if (hidden) "Rp ••" else "Rp ${formatJt(it)}"}", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun AccountsRow(accounts: List<AccountEntity>, s: JsonObject) {
    val balances = s.arr("balances")?.associate { it.jsonObject.text("id") to (it.jsonObject.long("balance") ?: 0) }.orEmpty()
    val total = accounts.sumOf { balances[it.id] ?: 0 }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 16.dp)) {
        item {
            GsCard(Modifier.width(224.dp).height(141.dp), color = Token.aksen) {
                Text("Rekening & wallet", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = Token.teksDiAksen)
                Text("${accounts.size} akun · siap pakai", style = MaterialTheme.typography.bodySmall, color = Token.teksDiAksen.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                Text(rupiah(total), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Token.teksDiAksen)
                s.long("month_spend")?.let { Text("Keluar ${if (LocalPrivacy.current.hidden) "Rp ••" else "Rp ${formatJt(it)}"} bulan ini", style = MaterialTheme.typography.bodySmall, color = Token.teksDiAksen.copy(alpha = 0.85f)) }
            }
        }
        items(accounts, key = { it.id }) { a ->
            GsCard(Modifier.width(224.dp).height(141.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(when (a.kind) { "ewallet" -> Ic.phone; "cash" -> Ic.cash; else -> Ic.bank }, size = 40.dp)
                    Text(a.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                Text(rupiah(balances[a.id] ?: 0), style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp), color = Token.teks)
                Text(when (a.kind) { "ewallet" -> "E-wallet"; "cash" -> "Tunai"; else -> "Rekening bank" }, style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            }
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onOpen: () -> Unit) {
    GsCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Perlu dicek", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
            Box(Modifier.padding(start = 8.dp).size(26.dp).clip(CircleShape).background(Token.isian), contentAlignment = Alignment.Center) {
                Text("$count", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup)
            }
            Spacer(Modifier.weight(1f))
            Text("Buka Inbox", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks)
            Icon(Ic.chevronRight, null, tint = Token.aksenTeks, modifier = Modifier.size(18.dp))
        }
        Text("$count transaksi menunggu konfirmasimu.", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup, modifier = Modifier.padding(top = 6.dp))
    }
}
