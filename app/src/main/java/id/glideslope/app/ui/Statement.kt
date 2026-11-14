// S-23 Impor mutasi bank (F-17) on the phone: pick the file, it is read on the server (password asked only when needed),
// choose what to bring in, one bar saves. The same /api/v1/statements/preview and /apply as the web.
package id.glideslope.app.ui

import id.glideslope.app.AppLock

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

private const val MAX_BYTES = 5 * 1024 * 1024
private val WIB_ZONE = ZoneId.of("Asia/Jakarta")
private val FILTERS = listOf("semua" to "Semua", "new" to "Baru", "match" to "Cocok", "maybe" to "Mungkin sama", "imported" to "Sudah diimpor")
private val ROW_FIELDS = listOf("key", "occurred_at", "amount", "direction", "balance", "merchant", "note", "kind", "text")

private class Picked(val name: String, val mime: String, val bytes: ByteArray)

private fun read(ctx: Context, uri: Uri): Picked? {
    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "mutasi"
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mime = if (name.lowercase().endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    return Picked(name, mime, bytes)
}

private fun defaultDecision(r: JsonObject) = when (r.text("status")) { "new" -> "create"; "match" -> "link"; else -> "skip" }

@Composable
fun StatementScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { Api(Settings(ctx), SessionStore(ctx)) }
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Picked?>(null) }
    var password by remember { mutableStateOf("") }
    var needPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<JsonObject?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    val decisions = remember { mutableStateMapOf<String, String>() }
    var filter by remember { mutableStateOf("semua") }
    var checkpoint by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    var picking by remember { mutableStateOf<JsonObject?>(null) }
    var pickingAccount by remember { mutableStateOf(false) }

    fun fail(r: ApiResult<*>): String? = when (r) {
        is ApiResult.Fail -> { needPassword = r.code == "PASSWORD_DIBUTUHKAN" || r.code == "PASSWORD_SALAH"; r.message }
        ApiResult.Offline -> "Tidak ada koneksi. Layar ini butuh internet."
        is ApiResult.Ok -> null
    }

    fun load(f: Picked, pw: String, account: String?) {
        busy = true
        error = null
        scope.launch {
            when (val r = api.previewStatement(f.bytes, f.name, f.mime, pw, account)) {
                is ApiResult.Ok -> {
                    val p = r.value.obj() ?: JsonObject(emptyMap())
                    needPassword = false
                    preview = p
                    accountId = p.text("account_id")
                    decisions.clear()
                    p.list("rows").forEach { decisions[it.text("key").orEmpty()] = defaultDecision(it) }
                    checkpoint = p["check"].obj()?.flag("ok") == true && p.long("closing_balance") != null
                }
                else -> error = fail(r)
            }
            busy = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { runCatching { read(ctx, uri) }.getOrNull() }
            when {
                picked == null -> error = "Berkas tidak bisa dibuka."
                picked.bytes.size > MAX_BYTES -> error = "Berkas terlalu besar (maksimal 5 MB)."
                else -> { file = picked; preview = null; password = ""; load(picked, "", null) }
            }
        }
    }
    fun choose() { AppLock.handOff(); launcher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }
    fun reset() { file = null; preview = null; needPassword = false; error = null; password = ""; result = null }

    fun save() {
        val p = preview ?: return
        val account = accountId ?: return
        busy = true
        error = null
        val body = buildJsonObject {
            put("account_id", account)
            put("bank", p.text("bank").orEmpty())
            put("period_from", p.text("period_from").orEmpty())
            put("period_to", p.text("period_to").orEmpty())
            put("opening_balance", p["opening_balance"] ?: JsonNull)
            put("closing_balance", p["closing_balance"] ?: JsonNull)
            put("checkpoint", checkpoint)
            put("checkpoint_date", p.text("period_to").orEmpty())
            put("rows", buildJsonArray {
                p.list("rows").filter { it.text("status") != "imported" && decisions[it.text("key")] != "skip" }.forEach { r ->
                    val d = decisions[r.text("key")] ?: "skip"
                    add(buildJsonObject {
                        ROW_FIELDS.forEach { k -> put(k, r[k] ?: JsonNull) }
                        put("decision", d)
                        put("link_id", if (d == "link") r["match_id"] ?: JsonNull else JsonNull)
                    })
                }
            })
        }
        scope.launch {
            when (val r = api.send("POST", "/api/v1/statements/apply", body.toString())) {
                is ApiResult.Ok -> result = r.value.obj()
                else -> error = fail(r)
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp).semantics { contentDescription = "Kembali" })
            Column(Modifier.weight(1f)) {
                Text("Impor mutasi bank", style = MaterialTheme.typography.headlineSmall)
                Text("Excel Mandiri atau PDF Jago", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
            }
        }

        val res = result
        if (res != null) {
            Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Token.masukLatar), contentAlignment = Alignment.Center) { Icon(Ic.circleCheck, null, tint = Token.masuk, modifier = Modifier.size(36.dp)) }
                Text("Mutasi tersimpan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
                Text(
                    "${res.long("created") ?: 0} transaksi baru, ${res.long("linked") ?: 0} ditautkan, ${res.long("skipped") ?: 0} dilewati.",
                    style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup, modifier = Modifier.padding(top = 8.dp),
                )
                PillButton("Selesai", true, onBack)
            }
            return@Column
        }

        val p = preview
        if (p == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Token.aksenLembut).border(2.dp, Token.aksenSedang, RoundedCornerShape(20.dp)).clickable(enabled = !busy) { choose() }.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Token.permukaan), contentAlignment = Alignment.Center) { Icon(Ic.upload, null, tint = Token.aksenTeks, modifier = Modifier.size(26.dp)) }
                    Text(if (file == null) "Pilih berkas mutasi" else "Ganti berkas", style = MaterialTheme.typography.titleMedium, color = Token.teks)
                    Text("Mandiri .xlsx · Jago .pdf · maksimal 5 MB. Berkas tidak disimpan.", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                }
                file?.let { f ->
                    GsCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(Ic.fileText, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(if (busy) "Membaca…" else "${maxOf(1, f.bytes.size / 1024)} KB", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                            }
                            Icon(Ic.x, "Buang berkas", tint = Token.teksRedup, modifier = Modifier.size(44.dp).clickable { reset() }.padding(12.dp))
                        }
                        if (needPassword) {
                            Spacer(Modifier.height(16.dp))
                            AuthField("Password berkas", password, { password = it }, "Tanggal lahir atau 6 digit kartu", error = error, keyboard = androidx.compose.ui.text.input.KeyboardType.Password, hidden = true, onDone = { load(f, password, null) })
                            Spacer(Modifier.height(12.dp))
                            PillButton(if (busy) "Membuka…" else "Buka berkas", password.isNotEmpty() && !busy) { load(f, password, null) }
                        }
                    }
                }
                if (error != null && !needPassword) InfoBanner(error.orEmpty(), warning = true)
            }
            return@Column
        }

        val rows = p.list("rows")
        val counts = p["counts"].obj()
        val shown = rows.filter { filter == "semua" || it.text("status") == filter }
        val chosen = rows.filter { it.text("status") != "imported" && (decisions[it.text("key")] ?: "skip") != "skip" }
        val nLink = chosen.count { decisions[it.text("key")] == "link" }
        val accounts = p.list("accounts")
        val account = accounts.firstOrNull { it.text("id") == accountId }
        val check = p["check"].obj()

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Token.aksenLembut), contentAlignment = Alignment.Center) {
                            Text((if (p.text("bank") == "mandiri") "M" else "J"), style = MaterialTheme.typography.titleMedium, color = Token.aksenTeks)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${if (p.text("bank") == "mandiri") "Mandiri" else "Jago"} •••• ${p.text("last4")}", style = MaterialTheme.typography.titleMedium)
                            Text("${fmtDate(p.text("period_from"))} – ${fmtDate(p.text("period_to"))} · ${rows.size} baris", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                        }
                        Text("Ganti", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks, modifier = Modifier.clickable { reset() }.padding(8.dp))
                    }
                    p.long("closing_balance")?.let { LineRow("Saldo akhir", value = rupiah(it)) }
                    HorizontalDivider(color = Token.garis)
                    LineRow("Simpan ke akun", value = account?.text("name") ?: "Pilih akun", valueColor = if (account == null) Token.galat else Token.aksenTeks, onClick = { pickingAccount = true })
                }
                InfoBanner(
                    if (check?.flag("ok") == true) "Saldo cocok: ${check.text("detail").orEmpty()}" else "Saldo tidak cocok: ${check?.text("detail").orEmpty()} Periksa berkas sebelum menyimpan.",
                    warning = check?.flag("ok") != true,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEach { (k, label) ->
                        val on = filter == k
                        val n = if (k == "semua") rows.size.toLong() else counts?.long(k) ?: 0L
                        Box(Modifier.clip(CircleShape).background(if (on) Token.aksen else Token.isian).clickable { filter = k }.heightIn(min = 40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("$label $n", style = MaterialTheme.typography.labelLarge, color = if (on) Token.teksDiAksen else Token.teksRedup)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if ((counts?.long("new") ?: 0L) > 0) Text("Impor semua yang baru", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks, modifier = Modifier.clickable { rows.filter { it.text("status") == "new" }.forEach { decisions[it.text("key").orEmpty()] = "create" } }.padding(vertical = 6.dp))
                    Text("Lewati semua", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks, modifier = Modifier.clickable { rows.forEach { decisions[it.text("key").orEmpty()] = "skip" } }.padding(vertical = 6.dp))
                }
                shown.groupBy { runCatching { Instant.parse(it.text("occurred_at")).atZone(WIB_ZONE).toLocalDate().toString() }.getOrDefault("") }.forEach { (day, dayRows) ->
                    Text(fmtDate(day), style = MaterialTheme.typography.labelLarge, color = Token.teksRedup)
                    GsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        dayRows.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(color = Token.garis)
                            StatementRow(r, decisions[r.text("key")] ?: "skip", onClick = { if (r.text("status") != "imported") picking = r })
                        }
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Token.permukaan).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${chosen.size - nLink} dibuat · $nLink ditautkan · ${rows.count { it.text("status") != "imported" } - chosen.size} dilewati", style = MaterialTheme.typography.labelLarge)
                if (p.long("closing_balance") != null && accountId != null) {
                    Row(Modifier.clickable { checkpoint = !checkpoint }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Checkbox(checkpoint, { checkpoint = it })
                        Text("Simpan saldo akhir sebagai titik cek", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Token.galat) }
                PillButton(if (busy) "Menyimpan…" else "Simpan ${chosen.size} baris", accountId != null && !busy && (chosen.isNotEmpty() || checkpoint), ::save)
            }
        }

        picking?.let { r ->
            val opts = buildList {
                add("skip" to "Lewati"); add("create" to "Buat baru")
                if (r.text("match_id") != null) add("link" to "Tautkan ke transaksi yang sudah ada")
            }
            PickDialog(r.text("merchant") ?: r.text("text") ?: "Baris", opts, onDismiss = { picking = null }) { decisions[r.text("key").orEmpty()] = it; picking = null }
        }
        if (pickingAccount) {
            PickDialog("Simpan ke akun", accounts.map { (it.text("id") ?: "") to ((it.text("name") ?: "") + (it.text("last4")?.let { l -> " ····$l" } ?: "")) }, onDismiss = { pickingAccount = false }) { id ->
                pickingAccount = false
                accountId = id
                file?.let { load(it, password, id) }
            }
        }
    }
}

@Composable
private fun StatementRow(r: JsonObject, decision: String, onClick: () -> Unit) {
    val incoming = r.text("direction") == "in"
    val status = r.text("status")
    val label = when (decision) { "create" -> "Buat baru"; "link" -> "Tautkan"; else -> "Lewati" }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(if (incoming) Ic.arrowDownLeft else Ic.arrowUpRight, bg = if (incoming) Token.masukLatar else Token.isian, fg = if (incoming) Token.masuk else Token.teksRedup, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(r.text("merchant") ?: r.text("text").orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                when (status) { "new" -> "Baru"; "match" -> "Cocok"; "maybe" -> "Mungkin sama"; else -> "Sudah diimpor" } +
                    (if (r.flag("before_opening")) " · sebelum saldo awal" else "") + (if (r.text("kind") == "transfer_unknown") " · dicek di Inbox" else ""),
                style = MaterialTheme.typography.bodySmall, color = Token.teksRedup,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (incoming) "+" else "−") + rupiah(r.long("amount") ?: 0L), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)), color = if (incoming) Token.masuk else Token.teks)
            if (status != "imported") Text(label, style = MaterialTheme.typography.labelMedium, color = if (decision == "skip") Token.teksPelan else Token.aksenTeks)
        }
    }
}
