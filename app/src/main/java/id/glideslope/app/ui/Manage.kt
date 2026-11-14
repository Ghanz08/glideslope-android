// Rekening & wallet (S-13), Detail aset (S-12), Pengaturan (S-20): everything that edits data, read from and written to the Go API.
package id.glideslope.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.core.formatRupiah
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

private val ASSET_TYPES = listOf(
    "money_market_fund" to "Reksa dana pasar uang", "fixed_income_fund" to "Reksa dana pendapatan tetap", "equity_fund" to "Reksa dana saham",
    "deposit" to "Deposito", "bond" to "Obligasi & SBN", "gold" to "Emas", "stock" to "Saham", "crypto" to "Kripto", "other" to "Lainnya",
)
private val QUOTES = listOf("" to "Tanpa harga publik", "reksadana" to "Reksa dana", "galeri24" to "Emas (Galeri24)", "indodax" to "Kripto (Indodax)", "yahoo" to "Saham (Yahoo)")

/** The form behind "+ Aset" (API-25): a new asset gets its first price from [unit_price]. */
fun assetFields(a: JsonObject?) = listOf(
    FieldSpec("name", "Nama aset", initial = a?.text("name").orEmpty()),
    FieldSpec("type", "Jenis", Kind.Choice(ASSET_TYPES), a?.text("type") ?: "money_market_fund"),
    FieldSpec("quantity", "Jumlah unit (1 untuk deposito)", Kind.Decimal, a?.dbl("quantity")?.toString() ?: "1"),
    FieldSpec("unit_price", "Harga per unit / nilai pokok", Kind.Decimal),
    FieldSpec("cost_basis", "Modal awal (opsional)", Kind.Money, a?.long("cost_basis")?.toString().orEmpty()),
    FieldSpec("maturity_date", "Jatuh tempo (deposito/obligasi)", Kind.Date, a?.text("maturity_date").orEmpty()),
    FieldSpec("platform", "Platform (opsional)", initial = a?.text("platform").orEmpty()),
)

// ---------------------------------------------------------------- Rekening & wallet

@Composable
fun AccountsScreen(onBack: () -> Unit) = RemoteScreen("Rekening & wallet", "/api/v1/layar/akun", onBack, "Saldo dihitung dari catatan, dicek dengan titik cek") { data, reload ->
    val accounts = data.obj()?.list("accounts").orEmpty()
    val w = rememberWriter()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var form by remember { mutableStateOf<JsonObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<JsonObject?>(null) }
    var action by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Akun baru") }
    w.message?.let { InfoBanner(it) }
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        if (accounts.isEmpty()) Text("Belum ada akun.", color = Token.teksRedup, modifier = Modifier.padding(vertical = 12.dp))
        // pockets (F-19) are drawn under their bank account; the parent shows its family total
        val top = accounts.filter { it.text("parent_id") == null }
        top.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val kind = when (a.text("kind")) { "ewallet" -> "E-wallet"; "cash" -> "Tunai"; else -> "Bank" }
            val pockets = accounts.filter { it.text("parent_id") == a.text("id") }
            LineRow(
                (a.text("name") ?: "Akun") + if (a.flag("archived")) " (arsip)" else "",
                listOfNotNull(kind, a.text("last4")?.let { "•••• $it" }, a.obj("label")?.text("text"), if (pockets.isNotEmpty()) "${pockets.size} kantong" else null).joinToString(" · "),
                rupiah(money(a, "balance")), onClick = { acting = a },
            )
            pockets.forEach { k ->
                Row(Modifier.padding(start = 16.dp)) {
                    Box(Modifier.weight(1f)) {
                        LineRow(
                            k.text("name") ?: "Kantong",
                            "Kantong${if (k.text("pocket_kind") == "bersama") " bersama · tidak dihitung di Dana tersisa" else ""}",
                            rupiah(money(k, "balance")), onClick = { acting = k },
                        )
                    }
                }
            }
        }
    }
    if (adding || form != null) {
        val a = form
        val fs = listOf(
            FieldSpec("name", "Nama akun", initial = a?.text("name").orEmpty()),
            FieldSpec("kind", "Jenis", Kind.Choice(listOf("bank" to "Bank", "ewallet" to "E-wallet", "cash" to "Tunai")), a?.text("kind") ?: "bank"),
            FieldSpec("last4", "4 digit terakhir (opsional)", Kind.Text, a?.text("last4").orEmpty()),
            // the server rewrites the opening balance with every save, so an edit carries the stored one
            FieldSpec("opening_balance", "Saldo awal", Kind.Money, a?.long("opening_balance")?.toString().orEmpty()),
            FieldSpec("opening_date", "Tanggal saldo awal", Kind.Date, a?.text("opening_date") ?: LocalDate.now().toString()),
        )
        FormDialog(if (a == null) "Akun baru" else "Ubah akun", fs, busy = w.busy, error = null, onDismiss = { adding = false; form = null }) { v ->
            val body = kotlinx.serialization.json.buildJsonObject {
                fs.forEach { f ->
                    val raw = v[f.key] ?: f.initial
                    // the server takes the opening balance as text of integer rupiah
                    if (f.key == "opening_balance") put(f.key, kotlinx.serialization.json.JsonPrimitive(digitsToLong(raw)?.toString() ?: "0")) else put(f.key, fieldJson(f.kind, raw))
                }
            }
            w.call(if (a == null) "POST" else "PATCH", if (a == null) "/api/v1/accounts" else "/api/v1/accounts/${a.text("id")}", body.toString(), "Akun disimpan.") { adding = false; form = null; reload() }
        }
    }
    acting?.let { a ->
        val opts = buildList {
            add("ubah" to "Ubah nama / jenis"); add("cek" to "Titik cek saldo")
            if (a.text("kind") == "ewallet") add("biaya" to "Biaya isi saldo")
            add("arsip" to if (a.flag("archived")) "Pulihkan" else "Arsipkan")
        }
        PickDialog(a.text("name") ?: "Akun", opts, onDismiss = { acting = null }) { act ->
            acting = null
            if (act == "arsip") w.call("POST", "/api/v1/accounts/${a.text("id")}/archive", """{"archive":${!a.flag("archived")}}""", "Tersimpan.") { reload() }
            else if (act == "ubah") scope.launch {
                when (val r = Api(Settings(ctx), SessionStore(ctx)).get("/api/v1/layar/akun/${a.text("id")}")) {
                    is ApiResult.Ok -> form = r.value.obj()?.obj("raw") ?: a
                    is ApiResult.Fail -> w.message = r.message
                    ApiResult.Offline -> w.message = "Tidak ada koneksi."
                }
            } else action = act to a
        }
    }
    action?.let { (act, a) ->
        val id = a.text("id")
        if (act == "cek") {
            val fs = listOf(FieldSpec("as_of", "Tanggal", Kind.Date, LocalDate.now().toString()), FieldSpec("balance", "Saldo sebenarnya", Kind.Money, money(a, "balance").toString()))
            FormDialog("Titik cek ${a.text("name")}", fs, busy = w.busy, onDismiss = { action = null }) { v ->
                w.call("POST", "/api/v1/accounts/$id/checkpoints", bodyOf(fs, v).toString()) { r ->
                    val d = (r as? JsonObject)?.long("difference") ?: 0
                    w.message = if (d == 0L) "Saldo cocok dengan catatan." else "Selisih ${formatRupiah(d)} dari catatan."
                    action = null; reload()
                }
            }
        } else {
            val fs = listOf(FieldSpec("fee", "Biaya per isi saldo", Kind.Money, "0", "Isi 0 bila bank yang memotong"))
            FormDialog("Biaya isi saldo", fs, busy = w.busy, onDismiss = { action = null }) { v ->
                w.call("PUT", "/api/v1/accounts/$id/topup-fee", bodyOf(fs, v).toString(), "Biaya disimpan.") { action = null; reload() }
            }
        }
    }
}

// ---------------------------------------------------------------- Detail aset

@Composable
fun AssetDetailScreen(id: String, onBack: () -> Unit) = RemoteScreen("Detail aset", "/api/v1/layar/aset/$id", onBack) { data, reload ->
    val o = data.obj() ?: return@RemoteScreen
    val a = o.obj("asset") ?: return@RemoteScreen
    val accounts = o.list("accounts")
    val w = rememberWriter()
    var dialog by remember { mutableStateOf<String?>(null) }
    val accountOptions = accounts.map { it.text("id").orEmpty() to (it.text("name") ?: "Akun") }
    val value = money(a, "value")
    val cost = a.long("cost_basis")
    GsCard {
        Text(a.text("name") ?: "Aset", style = MaterialTheme.typography.titleLarge)
        Text(ASSET_TYPES.firstOrNull { it.first == a.text("type") }?.second ?: "", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Spacer(Modifier.height(8.dp))
        BigAmount(value)
        if (cost != null && cost > 0) {
            val g = value - cost
            Text((if (g >= 0) "Untung " else "Rugi ") + rupiah(kotlin.math.abs(g)), color = if (g >= 0) Token.masuk else Token.galat, style = MaterialTheme.typography.bodyMedium)
        }
        a.text("price_as_of")?.let { Text("Harga per ${fmtDate(it)}${if (a.flag("stale")) " · perlu diperbarui" else ""}", style = MaterialTheme.typography.bodySmall, color = if (a.flag("stale")) Token.peringatan else Token.teksRedup) }
    }
    w.message?.let { InfoBanner(it) }
    GsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialog = "harga" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Perbarui harga") }
            OutlinedButton(onClick = { dialog = "beli" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Beli") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "cair" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cairkan") }
            OutlinedButton(onClick = { dialog = "ubah" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Ubah detail") }
        }
        if (a.text("quote_source") != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(enabled = !w.busy, onClick = { w.call("POST", "/api/v1/assets/$id/quote/apply", okText = "Harga publik dicatat.") { reload() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Pakai harga publik") }
        }
    }
    val prices = o.list("prices")
    if (prices.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Riwayat harga", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        prices.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(fmtDate(p.text("as_of")), value = rupiah(Math.round(p.dbl("unit_price") ?: 0.0)))
        }
    }
    OutlinedButton(onClick = { dialog = "arsip" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Arsipkan aset") }

    fun done(msg: String) { dialog = null; w.message = msg; reload() }
    when (dialog) {
        "harga" -> {
            val fs = listOf(FieldSpec("as_of", "Tanggal", Kind.Date, LocalDate.now().toString()), FieldSpec("unit_price", "Harga per unit", Kind.Decimal))
            FormDialog("Perbarui harga", fs, busy = w.busy, onDismiss = { dialog = null }) { v -> w.call("POST", "/api/v1/assets/$id/prices", bodyOf(fs, v).toString()) { done("Harga tersimpan.") } }
        }
        "beli" -> {
            val fs = listOf(
                FieldSpec("amount", "Jumlah dibayar", Kind.Money), FieldSpec("quantity", "Unit didapat", Kind.Decimal),
                FieldSpec("account_id", "Dari akun", Kind.Choice(accountOptions), accountOptions.firstOrNull()?.first.orEmpty()), FieldSpec("occurred_on", "Tanggal", Kind.Date, LocalDate.now().toString()),
            )
            FormDialog("Beli ${a.text("name")}", fs, busy = w.busy, onDismiss = { dialog = null }) { v -> w.call("POST", "/api/v1/assets/$id/purchase", bodyOf(fs, v).toString()) { done("Pembelian dicatat.") } }
        }
        "cair" -> {
            val fs = listOf(
                FieldSpec("amount", "Jumlah dicairkan", Kind.Money), FieldSpec("destination_account_id", "Masuk ke akun", Kind.Choice(accountOptions), accountOptions.firstOrNull()?.first.orEmpty()),
                FieldSpec("fee", "Biaya (opsional)", Kind.Money, "0"), FieldSpec("occurred_on", "Tanggal", Kind.Date, LocalDate.now().toString()),
            )
            FormDialog("Cairkan ${a.text("name")}", fs, busy = w.busy, onDismiss = { dialog = null }) { v -> w.call("POST", "/api/v1/assets/$id/redeem", bodyOf(fs, v).toString()) { done("Pencairan dicatat.") } }
        }
        "ubah" -> {
            val fs = listOf(
                FieldSpec("name", "Nama", initial = a.text("name").orEmpty()), FieldSpec("platform", "Platform", initial = a.text("platform").orEmpty()),
                FieldSpec("cost_basis", "Modal", Kind.Money, a.long("cost_basis")?.toString().orEmpty()),
                FieldSpec("quote_source", "Harga publik", Kind.Choice(QUOTES), a.text("quote_source").orEmpty()), FieldSpec("quote_symbol", "Kode (mis. BBCA.JK)", initial = a.text("quote_symbol").orEmpty()),
            )
            FormDialog("Ubah detail", fs, busy = w.busy, onDismiss = { dialog = null }) { v -> w.call("PATCH", "/api/v1/assets/$id/details", bodyOf(fs, v).toString()) { done("Tersimpan.") } }
        }
        "arsip" -> PickDialog("Arsipkan ${a.text("name")}?", listOf("ya" to "Ya, arsipkan"), onDismiss = { dialog = null }) {
            w.call("POST", "/api/v1/assets/$id/archive") { dialog = null; onBack() }
        }
    }
}
