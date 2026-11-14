// S-14 Scan nota and S-15 Tinjau nota (F-09): photo → read on the phone (ML Kit) → server validates → owner confirms.
package id.glideslope.app.ui

import id.glideslope.app.AppLock

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import id.glideslope.app.ocr.DeviceOcr
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

@Composable
fun ScanScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    BackHandler(enabled = busy == null, onBack = onBack)

    fun process(uri: Uri?) {
        if (uri == null) return
        error = null
        scope.launch {
            busy = "Membaca nota di HP…"
            val prepared = DeviceOcr.prepare(ctx, uri)
            if (prepared == null) { busy = null; error = "Foto tidak bisa dibaca atau lebih dari 5 MB. Coba foto lain."; return@launch }
            busy = "Mengirim ke server…"
            val api = Api(Settings(ctx), SessionStore(ctx))
            when (val r = api.uploadReceipt(prepared.jpeg, prepared.ocrJson)) {
                is ApiResult.Ok -> {
                    val id = (r.value as? JsonObject)?.text("id")
                    busy = null
                    if (id != null) onOpen(id) else error = "Nota terunggah tetapi server tidak mengirim nomornya."
                }
                is ApiResult.Fail -> { busy = null; error = r.message }
                ApiResult.Offline -> { busy = null; error = "Tidak ada koneksi. Nota belum terunggah." }
            }
            photo?.let { runCatching { File(it.path.orEmpty()).delete() } }
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) process(photo) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { process(it) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(enabled = busy == null, onClick = onBack).padding(12.dp))
            Column {
                Text("Scan nota", style = MaterialTheme.typography.headlineSmall)
                Text("Foto nota dibaca di HP, lalu dicocokkan dengan email", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
            }
        }
        GsCard {
            Text("Foto dikecilkan dan dibaca langsung di HP-mu, tanpa internet. Hasilnya selalu kamu tinjau sebelum dikonfirmasi.", color = Token.teksRedup, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            busy?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Token.aksenTeks, modifier = Modifier.padding(bottom = 12.dp)) }
            Button(
                enabled = busy == null,
                onClick = {
                    val dir = File(ctx.cacheDir, "nota").apply { mkdirs() }
                    val file = File.createTempFile("nota", ".jpg", dir)
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
                    photo = uri
                    AppLock.handOff(); take.launch(uri)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Icon(Ic.camera, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("Ambil foto") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = busy == null,
                onClick = { AppLock.handOff(); pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Pilih dari galeri") }
        }
        error?.let { InfoBanner(it, warning = true) }
    }
}

private fun JsonObject.numText(k: String) = long(k)?.toString().orEmpty()

@Composable
fun ReceiptReviewScreen(id: String, onBack: () -> Unit, onDone: () -> Unit, onSplit: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    RemoteScreen("Tinjau nota", "/api/v1/layar/nota/$id", onBack) { data, reload ->
        val root = data.obj() ?: return@RemoteScreen
        val r = root.obj("receipt") ?: run { GsCard { Text("Nota tidak ditemukan.", color = Token.teksRedup) }; return@RemoteScreen }
        val accounts = root.list("accounts")
        val categories = root.list("categories")
        val items = r.list("receipt_items")
        val status = r.text("status")
        var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.text("id")) }
        var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf<String?>(null) } // "akun" | "kategori"
        var editing by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var problems by remember { mutableStateOf<List<String>>(emptyList()) }
        var working by remember { mutableStateOf(false) }

        GsCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(r.text("merchant_name") ?: "Toko belum terbaca", style = MaterialTheme.typography.titleLarge)
                    Text(fmtDate(r.text("occurred_at")), style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
                }
                when (status) {
                    "extracted", "confirmed" -> Pill(if (status == "confirmed") "Dikonfirmasi" else "Terbaca", Ic.circleCheck, Token.masukLatar, Token.masuk)
                    "perlu_koreksi" -> Pill("Perlu dikoreksi", Ic.triangleAlert, Token.peringatanLatar, Token.peringatan)
                    else -> Pill("Gagal dibaca", Ic.triangleAlert, Token.galatLatar, Token.galat)
                }
            }
            Spacer(Modifier.height(12.dp))
            BigAmount(r.long("total") ?: 0L)
        }
        if (status == "gagal") {
            InfoBanner("Nota belum bisa dibaca. Coba baca ulang, atau isi manual.", warning = true)
        } else if (status == "perlu_koreksi" || problems.isNotEmpty()) {
            InfoBanner((problems.ifEmpty { listOf("Total belum cocok dengan item. Koreksi angkanya sebelum dikonfirmasi.") }).joinToString("\n"), warning = true)
        }
        message?.let { InfoBanner(it) }
        if (items.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items.forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider(color = Token.garis)
                val q = it.dbl("quantity") ?: 1.0
                LineRow(it.text("name") ?: "Item", if (q != 1.0) "${q.toString().removeSuffix(".0")} × ${rupiah(it.long("unit_price") ?: 0)}" else null, rupiah(it.long("line_total") ?: 0))
            }
            HorizontalDivider(color = Token.garis)
            r.long("subtotal")?.let { LineRow("Subtotal", value = rupiah(it)) }
            (r.long("service_charge") ?: 0).takeIf { it > 0 }?.let { LineRow("Biaya layanan", value = rupiah(it)) }
            (r.long("tax_amount") ?: 0).takeIf { it > 0 }?.let { LineRow("Pajak", value = rupiah(it)) }
            LineRow("Total", value = rupiah(r.long("total") ?: 0))
        }
        if (status == "confirmed" || status == "extracted") {
            OutlinedButton(enabled = !working, onClick = {
                working = true
                scope.launch {
                    val api = Api(Settings(ctx), SessionStore(ctx))
                    when (val res = api.send("POST", "/api/v1/splits", buildJsonObject { put("nota", id) }.toString())) {
                        is ApiResult.Ok -> (res.value as? JsonObject)?.text("id")?.let(onSplit)
                        is ApiResult.Fail -> message = res.message
                        ApiResult.Offline -> message = "Tidak ada koneksi."
                    }
                    working = false
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Bagi tagihan") }
        }
        if (status != "confirmed") {
            GsCard {
                LineRow("Akun", accounts.firstOrNull { it.text("id") == accountId }?.text("name") ?: "Pilih akun", onClick = { picking = "akun" })
                HorizontalDivider(color = Token.garis)
                LineRow("Kategori", categories.firstOrNull { it.text("id") == categoryId }?.text("name") ?: "Otomatis / tanpa", onClick = { picking = "kategori" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Koreksi angka") }
                if (status == "gagal") OutlinedButton(
                    enabled = !working,
                    onClick = {
                        working = true
                        scope.launch {
                            val api = Api(Settings(ctx), SessionStore(ctx))
                            message = when (val res = api.send("POST", "/api/v1/receipts/$id/retry")) { is ApiResult.Ok -> { reload(); null }; is ApiResult.Fail -> res.message; ApiResult.Offline -> "Tidak ada koneksi." }
                            working = false
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Baca ulang") }
            }
            Button(
                enabled = !working && accountId != null && status != "gagal",
                onClick = {
                    working = true
                    scope.launch {
                        val api = Api(Settings(ctx), SessionStore(ctx))
                        val body = buildJsonObject { put("account_id", accountId); categoryId?.let { put("category_id", it) } }.toString()
                        when (val res = api.send("POST", "/api/v1/receipts/$id/confirm", body)) {
                            is ApiResult.Ok -> onDone()
                            is ApiResult.Fail -> message = res.message
                            ApiResult.Offline -> message = "Tidak ada koneksi."
                        }
                        working = false
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (working) "Memproses…" else "Konfirmasi nota") }
        }

        when (picking) {
            "akun" -> PickDialog("Akun", accounts.map { it.text("id").orEmpty() to (it.text("name") ?: "Akun") }, { picking = null }) { accountId = it; picking = null }
            "kategori" -> PickDialog("Kategori", listOf("" to "Otomatis / tanpa") + categories.map { it.text("id").orEmpty() to (it.text("name") ?: "") }, { picking = null }) { categoryId = it.ifEmpty { null }; picking = null }
        }
        if (editing) CorrectDialog(r, onDismiss = { editing = false }) { body ->
            editing = false
            scope.launch {
                val api = Api(Settings(ctx), SessionStore(ctx))
                when (val res = api.send("PATCH", "/api/v1/receipts/$id", body)) {
                    is ApiResult.Ok -> { problems = ((res.value as? JsonObject)?.get("problems") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(); reload() }
                    is ApiResult.Fail -> message = res.message
                    ApiResult.Offline -> message = "Tidak ada koneksi."
                }
            }
        }
    }
}

/** The numbers the owner can fix (merchant, subtotal, service, tax, total); items stay as read. Table 5.4-I is checked by the server. */
@Composable
private fun CorrectDialog(r: JsonObject, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var merchant by remember { mutableStateOf(r.text("merchant_name").orEmpty()) }
    var subtotal by remember { mutableStateOf(r.numText("subtotal")) }
    var service by remember { mutableStateOf(r.numText("service_charge")) }
    var tax by remember { mutableStateOf(r.numText("tax_amount")) }
    var total by remember { mutableStateOf(r.numText("total")) }
    fun num(s: String) = s.filter { it.isDigit() }.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Koreksi angka") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Nama toko") }, singleLine = true)
                for ((label, value, set) in listOf(
                    Triple("Subtotal", subtotal) { v: String -> subtotal = v }, Triple("Biaya layanan", service) { v: String -> service = v },
                    Triple("Pajak", tax) { v: String -> tax = v }, Triple("Total", total) { v: String -> total = v },
                )) OutlinedTextField(value, { set(it.filter(Char::isDigit)) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val body = buildJsonObject {
                    put("merchant_name", merchant.ifBlank { null })
                    put("occurred_at", r.text("occurred_at"))
                    put("items", JsonArray(r.list("receipt_items").map {
                        buildJsonObject {
                            put("name", it.text("name") ?: "Item"); put("quantity", it.dbl("quantity") ?: 1.0)
                            put("unit_price", it.long("unit_price") ?: 0); put("line_total", it.long("line_total") ?: 0)
                        }
                    }))
                    put("subtotal", num(subtotal)); put("service_charge", num(service) ?: 0); put("tax_amount", num(tax) ?: 0)
                    put("tax_rate_pct", r.dbl("tax_rate_pct")); put("discount", r.long("discount") ?: 0); put("rounding", r.long("rounding") ?: 0)
                    put("total", num(total)); put("payment_method", r.text("payment_method"))
                }.toString()
                onSave(body)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
