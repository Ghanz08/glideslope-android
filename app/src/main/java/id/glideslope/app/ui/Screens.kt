// Sign-in, Beranda and Transaksi (F-14, phase N2: read-only from the local cache).
package id.glideslope.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.glideslope.app.BuildConfig
import id.glideslope.app.MainActivity
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.Db
import id.glideslope.app.data.SessionStore
import id.glideslope.app.data.TxListItem
import id.glideslope.app.sync.PullWorker
import id.glideslope.core.formatRupiah
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ID = Locale.forLanguageTag("id")
private val WIB = ZoneId.of("Asia/Jakarta")
private val jam = DateTimeFormatter.ofPattern("d MMM HH.mm", ID).withZone(WIB)
private val tanggal = DateTimeFormatter.ofPattern("d MMM yyyy", ID)

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })

@Composable
fun AuthScreen(activity: MainActivity, onSignedIn: () -> Unit) {
    val settings = remember { Settings(activity) }
    val api = remember { Api(settings, SessionStore(activity)) }
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf(settings.serverUrl.ifBlank { BuildConfig.SERVER_URL }) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needCode by rememberSaveable { mutableStateOf(SessionStore(activity).get()?.aal == "aal1") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun show(r: ApiResult<*>) {
        error = when (r) {
            is ApiResult.Fail -> r.message
            ApiResult.Offline -> "Server tidak terjangkau. Periksa koneksi dan alamat Glideslope."
            is ApiResult.Ok -> null
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title(if (needCode) "Kode autentikator" else "Masuk ke Glideslope")
        if (!needCode) {
            OutlinedTextField(
                server, { server = it.trim() }, label = { Text("Alamat Glideslope") }, placeholder = { Text("https://…") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Kata sandi") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Masukkan 6 angka dari aplikasi autentikator.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Kode") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Button(
            enabled = !busy && (if (needCode) code.length == 6 else server.startsWith("https://") && email.isNotBlank() && password.isNotEmpty()),
            onClick = {
                busy = true
                scope.launch {
                    if (!needCode) {
                        settings.serverUrl = server
                        val r = api.login(email.trim(), password)
                        show(r)
                        if (r is ApiResult.Ok) { password = ""; needCode = true }
                    } else {
                        val r = api.verifyCode(code)
                        show(r)
                        if (r is ApiResult.Ok && r.value.aal == "aal2") onSignedIn()
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (needCode) "Lanjut" else "Masuk") }
        if (needCode) TextButton(onClick = { needCode = false; code = ""; SessionStore(activity).set(null) }) { Text("Pakai akun lain") }
    }
}

private fun JsonObject.long(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
private fun JsonObject.text(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

@Composable
fun HomeScreen(pad: PaddingValues, onSignedOut: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { Db.get(ctx).sync() }
    val summary by dao.metaFlow("summary").collectAsState(initial = null)
    val syncedAt by dao.metaFlow("synced_at").collectAsState(initial = null)
    val syncError by dao.metaFlow("sync_error").collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val s = remember(summary) { summary?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } }
    val runway = s?.get("runway")?.jsonObject

    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Title("Beranda")
                Text(
                    syncedAt?.toLongOrNull()?.let { "Data per ${jam.format(Instant.ofEpochMilli(it))} WIB" } ?: "Menyiapkan data…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { PullWorker.now(ctx) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Perbarui") }
        }
        syncError?.takeIf { it.isNotBlank() }?.let { e ->
            Card(colors = CardDefaults.cardColors(containerColor = Token.galatLatar)) {
                Text(e.substringAfter('|'), color = Token.galat, modifier = Modifier.padding(16.dp))
            }
        }
        if (runway == null) {
            Text("Belum ada data. Pastikan HP tersambung ke internet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dana tersisa", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupiah(runway.long("funds") ?: 0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    runway.text("label")?.takeIf { it.isNotBlank() }?.let {
                        val ok = runway.text("status") == "sesuai"
                        Text((if (ok) "✓ " else "! ") + it, color = if (ok) Token.masuk else Token.peringatan, fontWeight = FontWeight.SemiBold)
                    }
                    runway.text("depletionDate")?.let {
                        Text("Cukup sampai ±${tanggal.format(LocalDate.parse(it))}")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Stat("Aman dipakai per hari", runway.long("safePerDay")?.let(::formatRupiah) ?: "—")
                    Stat("Rata-rata per bulan", s.get("burn")?.jsonObject?.long("burn")?.let(::formatRupiah) ?: "—")
                    Stat("Terpakai bulan ini", s.long("month_spend")?.let(::formatRupiah) ?: "—")
                }
            }
        }
        TextButton(
            onClick = {
                scope.launch {
                    Api(Settings(ctx), SessionStore(ctx)).logout()
                    Db.wipe(ctx)
                    onSignedOut()
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Keluar dan hapus data di HP ini") }
    }
}

@Composable
private fun Stat(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.SemiBold)
}

@Composable
fun TransactionsScreen(pad: PaddingValues) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val items by remember { Db.get(ctx).sync().recent() }.collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title("Transaksi") }
        if (items.isEmpty()) item { Text("Belum ada transaksi di HP ini.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(items, key = TxListItem::id) { TxRow(it) }
    }
}

@Composable
private fun TxRow(t: TxListItem) {
    val incoming = t.direction == "in"
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.merchantRaw ?: t.categoryName ?: t.note ?: "Tanpa keterangan", fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(jam.format(OffsetDateTime.parse(t.occurredAt)), t.accountName, t.categoryName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (t.status == "needs_review") Text("! Perlu dicek", color = Token.peringatan, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                (if (incoming) "+" else "−") + formatRupiah(t.amount),
                color = if (incoming) Token.masuk else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
