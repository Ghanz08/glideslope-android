// Screens that read the Go API directly (/api/v1/layar/…) instead of the local cache: Analitik, Anggaran, Langganan,
// Piutang, Rencana, Label, Sumber and the receipt review. They need a connection; the cached screens stay offline-first.
package id.glideslope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface Remote<out T> {
    data object Loading : Remote<Nothing>
    data class Err(val message: String, val offline: Boolean = false) : Remote<Nothing>
    data class Ok<T>(val value: T) : Remote<T>
}

/** GET [path] on the server; `reload()` fetches again (after a write, or on "Coba lagi"). */
@Composable
fun rememberRemote(path: String): Pair<Remote<JsonElement>, () -> Unit> {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val state = produceState<Remote<JsonElement>>(Remote.Loading, path, tick) {
        val api = Api(Settings(ctx), SessionStore(ctx))
        value = when (val r = api.get(path)) {
            is ApiResult.Ok -> Remote.Ok(r.value)
            is ApiResult.Fail -> Remote.Err(r.message)
            ApiResult.Offline -> Remote.Err("Tidak ada koneksi. Layar ini butuh internet.", offline = true)
        }
    }
    return state.value to { tick++ }
}

/** Back arrow, title and the three states of a server read: skeleton, error with "Coba lagi", content. */
@Composable
fun RemoteScreen(
    title: String,
    path: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.(data: JsonElement, reload: () -> Unit) -> Unit,
) {
    val (state, reload) = rememberRemote(path)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp).semantics { contentDescription = "Kembali" })
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val s = state) {
                Remote.Loading -> { SkeletonCard(); SkeletonCard(4) }
                is Remote.Err -> GsCard {
                    Text(s.message, color = Token.teks, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = reload, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Coba lagi") }
                }
                is Remote.Ok -> content(s.value, reload)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- JSON reading helpers shared by the screens

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.list(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.list(k: String): List<JsonObject> = this[k].list()
internal fun JsonObject.flag(k: String) = (this[k]?.takeIf { it !is JsonNull } as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
internal fun JsonObject.dbl(k: String) = (this[k]?.takeIf { it !is JsonNull } as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()

private val ID_LOCALE = Locale.forLanguageTag("id")
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", ID_LOCALE)

/** "2026-11-14" or an ISO instant → "14 Nov 2026" (the first ten characters carry the date). */
internal fun fmtDate(iso: String?): String = iso?.take(10)?.let { runCatching { LocalDate.parse(it).format(dateFmt) }.getOrNull() } ?: "–"

/** A label/value row inside a card; money goes through [rupiah] so privacy mode applies. */
@Composable
internal fun LineRow(title: String, sub: String? = null, value: String? = null, valueColor: androidx.compose.ui.graphics.Color = Token.teks, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Token.teks)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Token.teksRedup) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), color = valueColor) }
    }
}

/** A thin progress bar; [pct] 0..1+ (over 1 is clipped). */
@Composable
internal fun Meter(pct: Double, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(8.dp).androidx_clip()) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().androidx_bg(Token.isian))
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(pct.coerceIn(0.0, 1.0).toFloat()).fillMaxSize().androidx_bg(color))
    }
}

private fun Modifier.androidx_clip() = this.then(Modifier.clip(androidx.compose.foundation.shape.CircleShape))
private fun Modifier.androidx_bg(c: androidx.compose.ui.graphics.Color) = this.then(Modifier.background(c))
