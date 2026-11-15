// S-20 Pengaturan: rencana keuangan, keamanan (PIN), kategori, ekspor data, kesehatan sistem dan galat terakhir.
package id.glideslope.app.ui

import id.glideslope.app.AppLock

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val TONES = listOf("makan", "rumah", "hiburan", "belanja", "pendidikan", "kesehatan", "transportasi", "tagihan", "netral").map { it to it.replaceFirstChar(Char::uppercase) }

@Composable
fun SettingsScreen(onBack: () -> Unit) = RemoteScreen("Pengaturan", "/api/v1/layar/pengaturan", onBack) { data, reload ->
    val o = data.obj() ?: return@RemoteScreen
    val st = o.obj("settings") ?: return@RemoteScreen
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val w = rememberWriter()
    var dialog by remember { mutableStateOf<String?>(null) }
    var exportBusy by remember { mutableStateOf<String?>(null) }
    var pendingZip by remember { mutableStateOf<ByteArray?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = pendingZip
        if (uri != null && bytes != null) scope.launch {
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            w.message = "Ekspor tersimpan."
        }
        pendingZip = null
    }

    w.message?.let { InfoBanner(it) }
    GsCard {
        Text("Rencana keuangan", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        LineRow("Tanggal wisuda", value = fmtDate(st.text("graduation_date")))
        HorizontalDivider(color = Token.garis)
        LineRow("Dana cadangan", value = rupiah(money(st, "emergency_reserve")))
        HorizontalDivider(color = Token.garis)
        LineRow("Pengeluaran bulanan (manual)", value = st.long("burn_override")?.let { rupiah(it) } ?: "Otomatis")
        HorizontalDivider(color = Token.garis)
        LineRow("Batas nominal besar", value = rupiah(money(st, "large_amount_threshold")))
        HorizontalDivider(color = Token.garis)
        LineRow("Kunci otomatis", value = "${st.long("idle_timeout_minutes") ?: 5} menit")
        Spacer(Modifier.height(8.dp))
        Button(onClick = { dialog = "rencana" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ubah") }
    }
    GsCard {
        Text("Pembacaan otomatis", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        LineRow("Bantuan LLM untuk email sulit", value = if (st.flag("llm_enabled")) "Aktif" else "Mati")
        HorizontalDivider(color = Token.garis)
        LineRow("Hubungkan transfer otomatis", value = if (st.flag("auto_link_transfers")) "Aktif" else "Mati")
        HorizontalDivider(color = Token.garis)
        LineRow("Info bayar untuk tagihan", sub = st.text("payment_instructions") ?: "Belum diisi")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { dialog = "otomatis" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ubah") }
    }
    GsCard {
        Text("Keamanan", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        LineRow("PIN 6 angka", value = if (st.flag("pin_enabled")) "Aktif" else "Mati")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialog = "pin" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (st.flag("pin_enabled")) "Ganti PIN" else "Atur PIN") }
            if (st.flag("pin_enabled")) OutlinedButton(onClick = { dialog = "pin-off" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Matikan") }
        }
    }
    GsCard {
        Text("Ekspor data", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        Text(st.text("last_export_at")?.let { "Ekspor terakhir ${fmtDate(it)}" } ?: "Belum pernah dicadangkan", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { dialog = "ekspor" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ekspor semua data (.zip)") }
    }
    val cats = o.list("categories")
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Kategori · ${cats.size}", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        cats.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val (bg, fg) = Token.tone(c.text("tone_key"))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(0.dp).androidxSize().androidxClip().androidxBg(bg),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { Text((c.text("name") ?: "?").take(1).uppercase(), color = fg, style = MaterialTheme.typography.labelLarge) }
                Text(c.text("name") ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(if (c.text("kind") == "income") "Masuk" else "Keluar", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { dialog = "kategori" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("+ Kategori") }
    }
    val checks = o.obj("health")?.list("checks").orEmpty()
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Kesehatan sistem", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        checks.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(c.text("name") ?: "", c.text("detail"), if (c.flag("ok")) "OK" else "Perlu dicek", if (c.flag("ok")) Token.masuk else Token.galat)
        }
    }
    val errors = o.list("recent_errors")
    if (errors.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Galat terakhir", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        errors.take(8).forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(e.text("message") ?: "", "${e.text("source")} · ${e.text("route") ?: ""} · ${fmtDate(e.text("occurred_at"))}")
        }
    }

    fun done(msg: String) { dialog = null; w.message = msg; reload() }
    when (dialog) {
        "rencana" -> {
            val fs = listOf(
                FieldSpec("graduation_date", "Tanggal wisuda", Kind.Date, st.text("graduation_date").orEmpty()),
                FieldSpec("emergency_reserve", "Dana cadangan", Kind.Money, money(st, "emergency_reserve").toString()),
                FieldSpec("burn_override", "Pengeluaran bulanan manual", Kind.Money, st.long("burn_override")?.toString().orEmpty(), "Kosongkan untuk otomatis"),
                FieldSpec("large_amount_threshold", "Batas nominal besar", Kind.Money, money(st, "large_amount_threshold").toString()),
                FieldSpec("idle_timeout_minutes", "Kunci otomatis (menit)", Kind.Number, (st.long("idle_timeout_minutes") ?: 5).toString()),
            )
            FormDialog("Rencana keuangan", fs, busy = w.busy, onDismiss = { dialog = null }) { v -> w.call("PATCH", "/api/v1/settings", bodyOf(fs, v).toString()) { done("Pengaturan disimpan.") } }
        }
        "otomatis" -> {
            val fs = listOf(
                FieldSpec("llm_enabled", "Bantuan LLM untuk email sulit", Kind.Toggle, if (st.flag("llm_enabled")) "true" else "false"),
                FieldSpec("auto_link_transfers", "Hubungkan transfer otomatis", Kind.Toggle, if (st.flag("auto_link_transfers")) "true" else "false"),
                FieldSpec("payment_instructions", "Info bayar (mis. Transfer ke BNI a.n. …)", initial = st.text("payment_instructions").orEmpty()),
            )
            FormDialog("Pembacaan otomatis", fs, busy = w.busy, onDismiss = { dialog = null }) { v -> w.call("PATCH", "/api/v1/settings", bodyOf(fs, v).toString()) { done("Pengaturan disimpan.") } }
        }
        "pin" -> {
            val fs = listOf(FieldSpec("pin", "PIN baru (6 angka)", Kind.Number))
            FormDialog("Atur PIN", fs, busy = w.busy, onDismiss = { dialog = null }) { v ->
                val pin = v["pin"].orEmpty()
                if (pin.length != 6) w.message = "PIN harus 6 angka." else w.call("PUT", "/api/v1/settings/pin", buildJsonObject { put("pin", pin) }.toString()) { id.glideslope.app.AppLock.setPinEnabled(ctx, true); done("PIN disimpan.") }
            }
        }
        "pin-off" -> PickDialog("Matikan PIN?", listOf("ya" to "Ya, matikan"), onDismiss = { dialog = null }) {
            w.call("PUT", "/api/v1/settings/pin", """{"pin":null}""") { id.glideslope.app.AppLock.setPinEnabled(ctx, false); done("PIN dimatikan.") }
        }
        "kategori" -> {
            val fs = listOf(FieldSpec("name", "Nama kategori"), FieldSpec("kind", "Jenis", Kind.Choice(listOf("expense" to "Pengeluaran", "income" to "Pemasukan")), "expense"), FieldSpec("tone_key", "Warna", Kind.Choice(TONES), "netral"))
            FormDialog("Kategori baru", fs, busy = w.busy, onDismiss = { dialog = null }) { v ->
                val b = bodyOf(fs, v).toMutableMap().also { it["icon_key"] = kotlinx.serialization.json.JsonPrimitive("tag") }
                w.call("POST", "/api/v1/categories", JsonObject(b).toString()) { done("Kategori ditambahkan.") }
            }
        }
        "ekspor" -> {
            val pin = st.flag("pin_enabled")
            val fs = listOf(FieldSpec("secret", if (pin) "PIN untuk verifikasi ulang" else "Kode autentikator (6 angka)", Kind.Number))
            FormDialog("Ekspor data", fs, submit = "Ekspor", busy = exportBusy != null, error = exportBusy?.takeIf { it != "…" }, onDismiss = { dialog = null; exportBusy = null }) { v ->
                exportBusy = "…"
                scope.launch {
                    val body = buildJsonObject { put(if (pin) "pin" else "code", v["secret"].orEmpty()) }.toString()
                    when (val r = Api(Settings(ctx), SessionStore(ctx)).download("/api/v1/export", body)) {
                        is ApiResult.Ok -> { pendingZip = r.value; dialog = null; exportBusy = null; AppLock.handOff(); save.launch("glideslope-export.zip") }
                        is ApiResult.Fail -> exportBusy = r.message
                        ApiResult.Offline -> exportBusy = "Tidak ada koneksi."
                    }
                }
            }
        }
    }
}

private fun Modifier.androidxSize() = this.then(Modifier.size(36.dp))
private fun Modifier.androidxClip() = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)))
private fun Modifier.androidxBg(c: androidx.compose.ui.graphics.Color) = this.then(Modifier.background(c))
