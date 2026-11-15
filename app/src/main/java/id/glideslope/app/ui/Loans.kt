// F-20 Utang-piutang on the phone: who owes whom (net per person), one person with their loans, and the actions. Online only, like
// the other server screens; the same /api/v1 endpoints as the web.
package id.glideslope.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import id.glideslope.app.startHandOff
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun rp(n: Long) = id.glideslope.core.formatRupiah(n)

@Composable
fun LoansScreen(onBack: () -> Unit, onOpen: (String) -> Unit) = RemoteScreen("Utang-piutang", "/api/v1/layar/utang-piutang", onBack, "Siapa berutang berapa") { data, _ ->
    val o = data.obj() ?: return@RemoteScreen
    val people = o.list("contacts")
    GsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("Di teman", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                Text(rupiah(money(o, "owed_to_me")), style = MaterialTheme.typography.titleLarge, color = Token.masuk)
                Text("Tidak dihitung di dana tersisa", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("Utang ke teman", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                Text(rupiah(money(o, "i_owe")), style = MaterialTheme.typography.titleLarge, color = if (money(o, "i_owe") > 0) Token.peringatan else Token.teks)
                Text("Dikurangkan dari dana tersisa", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            }
        }
    }
    if (people.isEmpty()) GsCard { Text("Belum ada utang-piutang. Buka detail transaksi lalu pilih Pinjamkan atau Pinjam.", color = Token.teksRedup) }
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        people.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val net = money(p, "net")
            val sub = listOfNotNull(
                if (net > 0) "berutang ke Anda" else if (net < 0) "Anda berutang" else "impas",
                if (p.flag("overdue")) "lewat tanggal kembali" else null,
                (p.long("open_proposals") ?: 0L).takeIf { it > 0 }?.let { "$it usulan di Inbox" },
            ).joinToString(" · ")
            LineRow(p.text("name") ?: "Teman", sub, rupiah(kotlin.math.abs(net)), valueColor = if (net > 0) Token.masuk else if (net < 0) Token.peringatan else Token.teks, onClick = { onOpen(p.text("id").orEmpty()) })
        }
    }
}

@Composable
fun ContactScreen(id: String, onBack: () -> Unit) = RemoteScreen("Utang-piutang", "/api/v1/contacts/$id", onBack) { data, reload ->
    val c = data.obj() ?: return@RemoteScreen
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf<JsonObject?>(null) }
    var cancelling by remember { mutableStateOf<JsonObject?>(null) }
    var accounts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    val loans = c.list("loans")
    val payments = c.list("payments")
    val owed = money(c, "owed_to_me")
    val owe = money(c, "i_owe")
    val net = owed - owe
    fun call(method: String, path: String, body: String = "{}", ok: String) = scope.launch {
        message = when (val r = Api(Settings(ctx), SessionStore(ctx)).send(method, path, body)) {
            is ApiResult.Ok -> { reload(); ok }
            is ApiResult.Fail -> r.message
            ApiResult.Offline -> "Tidak ada koneksi."
        }
    }
    GsCard {
        Text(c.text("name") ?: "Teman", style = MaterialTheme.typography.headlineSmall)
        Text(if (net > 0) "berutang ke Anda" else if (net < 0) "Anda berutang" else "tidak ada sisa", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Text(rupiah(kotlin.math.abs(net)), style = MaterialTheme.typography.displaySmall, color = if (net > 0) Token.masuk else if (net < 0) Token.peringatan else Token.teks)
    }
    message?.let { InfoBanner(it) }
    if (owed > 0 && owe > 0) GsCard {
        Text("Saling berutang: ${c.text("name")} berutang ${rp(owed)}, Anda berutang ${rp(owe)}.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { call("POST", "/api/v1/contacts/$id/offset", ok = "Saling hapus dicatat.") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Saling hapus ${rp(minOf(owed, owe))}") }
    }
    val open = loans.filter { it.text("status") == "open" || it.text("status") == "partially_paid" }
    if (open.isEmpty()) GsCard { Text("Tidak ada pinjaman terbuka.", color = Token.teksRedup) }
    loans.forEach { l ->
        val isOpen = l in open
        val amount = money(l, "amount")
        val rem = money(l, "remaining")
        val lent = l.text("direction") == "piutang"
        GsCard {
            Text(if (lent) "Piutang · dipinjamkan" else "Utang · dipinjam", style = MaterialTheme.typography.labelLarge, color = if (lent) Token.masuk else Token.peringatan)
            Text(l.text("split_title")?.let { "Bagi tagihan: $it" } ?: l.text("note") ?: if (lent) "Dipinjamkan" else "Dipinjam", style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull("sejak ${fmtDate(l.text("tx_at") ?: l.text("created_at"))}", l.text("due_on")?.let { "kembali ${fmtDate(it)}" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            Spacer(Modifier.height(8.dp))
            Text("${rupiah(rem)} dari ${rupiah(amount)}", style = MaterialTheme.typography.titleLarge)
            Meter(if (amount > 0) (amount - rem).toDouble() / amount else 0.0, Token.masuk)
            payments.filter { it.text("receivable_id") == l.text("id") }.forEach { p ->
                LineRow(fmtDate(p.text("at")), if (p.text("method") == "saling_hapus") "saling hapus" else if (lent) "cicilan masuk" else "cicilan dibayar", rupiah(money(p, "amount")))
            }
            if (isOpen) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            when (val r = Api(Settings(ctx), SessionStore(ctx)).get("/api/v1/layar/utang-piutang")) {
                                is ApiResult.Ok -> { accounts = r.value.obj()?.list("accounts").orEmpty(); paying = l }
                                is ApiResult.Fail -> message = r.message
                                ApiResult.Offline -> message = "Tidak ada koneksi."
                            }
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (lent) "Tandai lunas tunai" else "Bayar tunai") }
                    if (lent) OutlinedButton(onClick = {
                        val phone = c.text("phone_digits").orEmpty()
                        val text = "Halo ${c.text("name")}, mengingatkan sedikit soal pinjaman kemarin ya. Sisanya ${rp(rem)}.${l.text("note")?.let { " Kesepakatannya: $it." } ?: ""} Kalau sudah ada, kabari ya. Terima kasih!"
                        ctx.startHandOff(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(text)}")))
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("WhatsApp") }
                }
                if (l.text("origin") == "pinjaman" && payments.none { it.text("receivable_id") == l.text("id") }) {
                    Text("Batalkan pinjaman", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks,
                        modifier = Modifier.clickable { cancelling = l }.padding(vertical = 12.dp))
                }
            }
        }
    }
    cancelling?.let { l ->
        ConfirmDialog("Batalkan pinjaman?", "Transaksinya kembali ke Inbox sebagai transaksi biasa.", "Batalkan pinjaman", onDismiss = { cancelling = null }) {
            cancelling = null
            call("DELETE", "/api/v1/loans/${l.text("id")}", ok = "Pinjaman dibatalkan; transaksinya kembali ke Inbox.")
        }
    }
    paying?.let { l ->
        PickDialog(if (l.text("direction") == "piutang") "Uang masuk ke akun mana?" else "Dibayar dari akun mana?",
            accounts.filter { it.text("kind") != "credit" }.map { (it.text("id") ?: "") to (it.text("name") ?: "Akun") }, onDismiss = { paying = null }) { acc ->
            paying = null
            call("POST", "/api/v1/receivables/${l.text("id")}/repayments", buildJsonObject { put("amount", money(l, "remaining")); put("account_id", acc) }.toString(), "Dicatat.")
        }
    }
}
