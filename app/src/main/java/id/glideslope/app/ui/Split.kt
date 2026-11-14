// S-15 Bagi tagihan (F-10): choose who joins and who had which item, let the server work out the shares (Table 5.4-D),
// then share: one receivable per friend and a WhatsApp message you send yourself.
package id.glideslope.app.ui

import id.glideslope.app.startHandOff

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.core.formatRupiah
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where a new split starts: a scanned receipt (or, from a transaction's detail, that transaction). */
@Composable
fun SplitStartScreen(onBack: () -> Unit, onOpen: (String) -> Unit) = RemoteScreen("Bagi tagihan", "/api/v1/layar/nota", onBack, "Pilih nota yang mau dibagi") { data, _ ->
    val w = rememberWriter()
    val recent = data.obj()?.list("recent").orEmpty()
    w.message?.let { InfoBanner(it, warning = true) }
    if (recent.isEmpty()) GsCard { Text("Belum ada nota. Scan nota dulu, atau buka detail transaksi lalu pilih Bagi tagihan.", color = Token.teksRedup) }
    else GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        recent.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(r.text("merchant_name") ?: "Nota", fmtDate(r.text("occurred_at") ?: r.text("created_at")), rupiah(money(r, "total")), onClick = {
                w.call("POST", "/api/v1/splits", buildJsonObject { put("nota", r.text("id")) }.toString()) { res -> (res as? JsonObject)?.text("id")?.let(onOpen) }
            })
        }
    }
}

/** Starts a split from a transaction and opens it; used by the transaction detail. */
fun startSplitFromTransaction(w: Writer, txId: String, onOpen: (String) -> Unit) =
    w.call("POST", "/api/v1/splits", buildJsonObject { put("transaksi", txId) }.toString()) { res -> (res as? JsonObject)?.text("id")?.let(onOpen) }

private class Person(var name: String, var phone: String, val owner: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(id: String, onBack: () -> Unit) = RemoteScreen("Bagi tagihan", "/api/v1/layar/bagi/$id", onBack) { data, reload ->
    val root = data.obj() ?: return@RemoteScreen
    val sp = root.obj("split") ?: return@RemoteScreen
    val ctx = LocalContext.current
    val w = rememberWriter()
    val shared = sp.text("status") != "draft"
    val receipt = sp.obj("receipts")
    val items = receipt?.list("receipt_items").orEmpty()
    val existing = sp.list("split_participants")
    val people = remember(data) { mutableStateListOf<Person>().apply {
        if (existing.isEmpty()) add(Person("Aku", "", true)) else existing.forEach { add(Person(it.text("name") ?: "", it.text("phone_digits").orEmpty(), it.flag("is_owner"))) }
    } }
    // assignments as "itemId|participantIndex"
    val picked = remember(data) { mutableStateListOf<String>().apply {
        val idToIndex = existing.mapIndexed { i, p -> p.text("id") to i }.toMap()
        sp.list("split_assignments").forEach { a -> idToIndex[a.text("participant_id")]?.let { add("${a.text("receipt_item_id")}|$it") } }
    } }
    var title by remember(data) { mutableStateOf(sp.text("title").orEmpty()) }
    var taxBase by remember(data) { mutableStateOf(sp.text("tax_base") ?: "items_plus_service") }

    fun body(): String = buildJsonObject {
        put("title", title); put("tax_base", taxBase)
        put("participants", JsonArray(people.map { p ->
            buildJsonObject { put("name", p.name); put("phone_digits", p.phone.filter(Char::isDigit).ifEmpty { null }); put("is_owner", p.owner) }
        }))
        put("assignments", JsonArray(picked.map { k ->
            val (item, who) = k.split('|')
            buildJsonObject { put("item_id", item); put("participant", who.toInt()); put("weight", 1) }
        }))
    }.toString()

    GsCard {
        Text(title.ifBlank { "Tagihan" }, style = MaterialTheme.typography.titleLarge)
        Text("${fmtDate(sp.text("occurred_on"))} · ${if (shared) "Sudah dibagikan" else "Draf"}", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Spacer(Modifier.height(8.dp))
        BigAmount(money(sp, "total"))
    }
    w.message?.let { InfoBanner(it) }

    if (!shared) {
        GsCard {
            OutlinedTextField(title, { title = it.take(60) }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("Dengan layanan", "Hanya item"), if (taxBase == "items") 1 else 0) { taxBase = if (it == 1) "items" else "items_plus_service" }
        }
        GsCard {
            Text("Peserta", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
            people.forEachIndexed { i, p ->
                var name by remember(i, people.size) { mutableStateOf(p.name) }
                var phone by remember(i, people.size) { mutableStateOf(p.phone) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; p.name = it }, label = { Text(if (p.owner) "Kamu" else "Nama") }, singleLine = true, modifier = Modifier.weight(1f), enabled = !p.owner || true)
                    if (!p.owner) OutlinedTextField(phone, { phone = it.filter(Char::isDigit); p.phone = phone }, label = { Text("WhatsApp") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                }
                if (!p.owner) OutlinedButton(onClick = {
                    people.removeAt(i); picked.removeAll { it.endsWith("|$i") }
                    // later participants shift down by one
                    val shifted = picked.map { k -> val (it, who) = k.split('|'); if (who.toInt() > i) "$it|${who.toInt() - 1}" else k }
                    picked.clear(); picked.addAll(shifted)
                }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Hapus ${p.name.ifBlank { "peserta" }}") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { people.add(Person("Teman ${people.size}", "", false)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("+ Teman") }
        }
        GsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Siapa ikut tiap item", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                Text("Bagi rata", color = Token.aksenTeks, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickableNoRipple {
                    picked.clear(); items.forEach { it2 -> people.indices.forEach { p -> picked.add("${it2.text("id")}|$p") } }
                })
            }
            items.forEachIndexed { n, it2 ->
                if (n > 0) HorizontalDivider(color = Token.garis, modifier = Modifier.padding(vertical = 4.dp))
                LineRow(it2.text("name") ?: "Item", value = rupiah(money(it2, "line_total")))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEachIndexed { p, person ->
                        val key = "${it2.text("id")}|$p"
                        FilterChip(selected = key in picked, onClick = { if (key in picked) picked.remove(key) else picked.add(key) }, label = { Text(if (person.owner) "Kamu" else person.name.ifBlank { "Teman" }) })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !w.busy, onClick = {
                w.call("PATCH", "/api/v1/splits/$id", body()) { r ->
                    val un = ((r as? JsonObject)?.get("unassigned") as? JsonArray)?.size ?: 0
                    w.message = if (un > 0) "$un item belum ditugaskan." else "Draf disimpan, bagian tiap orang sudah dihitung."
                    reload()
                }
            }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Simpan draf") }
            Button(enabled = !w.busy && items.isNotEmpty() && picked.isNotEmpty(), onClick = {
                w.call("POST", "/api/v1/splits/$id/share", body(), "Dibagikan. Piutang dibuat.") { reload() }
            }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Bagikan") }
        }
        Text("Bagikan membuat piutang untuk tiap teman; pesan WhatsApp kamu kirim sendiri.", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
    }

    // the result: each person's share, and a ready WhatsApp message per friend
    val withShare = existing.filter { it.dbl("share_amount") != null }
    if (withShare.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Per orang", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        val pay = root.text("payment_instructions")
        withShare.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val share = Math.round(p.dbl("share_amount") ?: 0.0)
            LineRow(if (p.flag("is_owner")) "Kamu" else p.text("name") ?: "Teman", if (p.flag("is_owner")) "Dicatat sebagai pengeluaran" else "Jadi piutang", rupiah(share))
            if (shared && !p.flag("is_owner") && share > 0) OutlinedButton(onClick = {
                val text = "Halo ${p.text("name")}, ini bagianmu untuk ${sp.text("title")} (${fmtDate(sp.text("occurred_on"))}): ${formatRupiah(share)}." + (pay?.let { " $it" } ?: "") + " Terima kasih!"
                ctx.startHandOff(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${p.text("phone_digits").orEmpty()}?text=${Uri.encode(text)}")))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Kirim via WhatsApp ke ${p.text("name")}") }
        }
    }
    if (shared && sp.text("status") == "shared") OutlinedButton(onClick = { w.call("POST", "/api/v1/splits/$id/cancel", okText = "Tagihan dibatalkan.") { reload() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Batalkan tagihan") }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))
