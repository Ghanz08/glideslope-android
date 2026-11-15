// S-24 Log aktivitas (F-18) on the phone: the same /api/v1/layar/log the web page reads, newest first, grouped by day.
package id.glideslope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WIB = ZoneId.of("Asia/Jakarta")
private val ID = Locale.forLanguageTag("id")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss", ID)

private val CATEGORIES = listOf("" to "Semua", "aksi" to "Aksi", "keamanan" to "Keamanan", "sistem" to "Sistem")

@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { Api(Settings(ctx), SessionStore(ctx)) }
    val scope = rememberCoroutineScope()
    var kategori by remember { mutableStateOf("") }
    var debug by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<JsonObject>() }
    var counts by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var next by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(cursor: String?) {
        busy = true
        error = null
        val q = buildString {
            append("/api/v1/layar/log?limit=50")
            if (kategori.isNotEmpty()) append("&kategori=$kategori")
            if (debug) append("&debug=true")
            if (cursor != null) append("&cursor=$cursor")
        }
        when (val r = api.get(q)) {
            is ApiResult.Ok -> {
                val o = r.value.obj()
                if (cursor == null) entries.clear()
                entries.addAll(o?.list("entries").orEmpty())
                next = o?.text("next_cursor")
                counts = o?.get("counts").obj()?.mapValues { it.value.toString().toLongOrNull() ?: 0L }.orEmpty()
            }
            is ApiResult.Fail -> error = r.message
            ApiResult.Offline -> error = "Tidak ada koneksi. Layar ini butuh internet."
        }
        busy = false
    }
    LaunchedEffect(kategori, debug) { load(null) }
    androidx.compose.runtime.DisposableEffect(kategori, debug) { PageRefresh.reload = { scope.launch { load(null) } }; onDispose { PageRefresh.reload = null } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp).semantics { contentDescription = "Kembali" })
            Column(Modifier.weight(1f)) {
                Text("Log aktivitas", style = MaterialTheme.typography.headlineSmall)
                Text("Aksi, keamanan dan pekerjaan sistem", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = counts.values.sum()
            CATEGORIES.forEach { (key, label) ->
                val on = key == kategori
                val n = if (key.isEmpty()) total else counts[key] ?: 0L
                Box(
                    Modifier.clip(CircleShape).background(if (on) Token.aksen else Token.isian).clickable { kategori = key }.heightIn(min = 40.dp).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$label $n", style = MaterialTheme.typography.labelLarge, color = if (on) Token.teksDiAksen else Token.teksRedup) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let {
                GsCard {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { load(null) } }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Coba lagi") }
                }
            }
            if (busy && entries.isEmpty()) SkeletonCard(5)
            if (!busy && error == null && entries.isEmpty()) GsCard { Text("Belum ada catatan untuk saringan ini.", color = Token.teksRedup) }
            val days = entries.groupBy { runCatching { Instant.parse(it.text("at")).atZone(WIB).toLocalDate() }.getOrNull() }
            days.forEach { (day, rows) ->
                Text(day?.format(dayFmt) ?: "–", style = MaterialTheme.typography.labelLarge, color = Token.teksRedup, modifier = Modifier.padding(top = 4.dp))
                GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    rows.forEachIndexed { i, e ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(color = Token.garis)
                        LogRow(e)
                    }
                }
            }
            next?.let { c ->
                Button(onClick = { scope.launch { load(c) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (busy) "Memuat…" else "Lebih lama") }
            }
            Text(
                if (debug) "Sembunyikan yang diabaikan" else "Tampilkan yang diabaikan", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks,
                modifier = Modifier.fillMaxWidth().clickable { debug = !debug }.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LogRow(e: JsonObject) {
    val level = e.text("level")
    val dot = when (level) { "galat" -> Token.galat; "peringatan" -> Token.peringatan; "debug" -> Token.teksPelan; else -> Token.aksen }
    val time = runCatching { Instant.parse(e.text("at")).atZone(WIB).format(timeFmt) }.getOrDefault("")
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(e.text("summary").orEmpty(), style = MaterialTheme.typography.bodyLarge, color = Token.teks)
            Text("$time WIB · ${e.text("category")} · ${e.text("source")}", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
        }
    }
}
