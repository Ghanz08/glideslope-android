// The screens under "Lainnya" (S-10, S-12 to S-18): Analitik, Anggaran, Langganan, Piutang, Rencana, Label, Sumber.
// All read /api/v1/layar/* (F-15.4: raw data, formatted here) and need a connection.
package id.glideslope.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import id.glideslope.core.formatPct
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun categoryNames(categories: List<JsonObject>) = categories.associate { it.text("id").orEmpty() to (it.text("name") ?: "Tanpa kategori") }
internal fun money(o: JsonObject, k: String) = o.long(k) ?: 0L

// ---------------------------------------------------------------- Anggaran (F-06.6)

@Composable
fun BudgetScreen(onBack: () -> Unit) = RemoteScreen("Anggaran", "/api/v1/layar/anggaran", onBack, "Ketuk kategori untuk memakai angka sendiri") { data, reload ->
    val o = data.obj() ?: return@RemoteScreen
    val names = categoryNames(o.list("categories"))
    val rows = o.list("rows")
    val w = rememberWriter()
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    GsCard {
        Text("Anggaran bulan ini", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Text(o.long("allowance")?.let { rupiah(it) } ?: "Belum bisa dihitung", style = MaterialTheme.typography.headlineSmall)
        Text("Terpakai ${rupiah(money(o, "month_spend"))}", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
    }
    w.message?.let { InfoBanner(it) }
    if (rows.isEmpty()) GsCard { Text("Belum ada pengeluaran bulan ini untuk dibandingkan.", color = Token.teksRedup) }
    else GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val pct = r.dbl("pct") ?: 0.0
            val color = when { pct >= 100 -> Token.galat; pct >= 80 -> Token.peringatan; else -> Token.aksenTeks }
            Column(Modifier.clickable { editing = r }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(names[r.text("category_id")] ?: "Tanpa kategori", style = MaterialTheme.typography.bodyLarge)
                    Text("${rupiah(money(r, "spent"))} / ${rupiah(money(r, "budget"))}", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
                }
                Meter(pct / 100.0, color)
                Text(if (r.flag("auto")) "Otomatis dari runway · ${formatPct(pct)}" else "Anggaran sendiri · ${formatPct(pct)}", style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
    editing?.let { r ->
        val fields = listOf(FieldSpec("amount", "Anggaran per bulan", Kind.Money, if (r.flag("auto")) "" else money(r, "budget").toString(), "Kosongkan untuk kembali otomatis"))
        FormDialog(names[r.text("category_id")] ?: "Anggaran", fields, busy = w.busy, onDismiss = { editing = null }) { v ->
            w.call("PUT", "/api/v1/budgets/${r.text("category_id")}", bodyOf(fields, v).toString(), "Anggaran disimpan.") { editing = null; reload() }
        }
    }
}

// ---------------------------------------------------------------- Langganan (F-11)

@Composable
fun SubscriptionsScreen(onBack: () -> Unit) = RemoteScreen("Langganan", "/api/v1/layar/langganan", onBack, "Terdeteksi otomatis dari email dan notifikasi") { data, reload ->
    val subs = data.obj()?.list("subscriptions").orEmpty()
    val active = subs.filter { it.text("status") == "active" }
    val suggested = subs.filter { it.text("status") == "suggested" }
    val perMonth = active.sumOf { s ->
        val a = money(s, "avg_amount")
        when (s.text("period")) { "yearly" -> a / 12; "weekly" -> a * 52 / 12; else -> a }
    }
    val w = rememberWriter()
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<JsonObject?>(null) }
    GsCard {
        Text("Total langganan aktif", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Text("${rupiah(perMonth)} /bln", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("+ Tambah") }
            OutlinedButton(enabled = !w.busy, onClick = { w.call("POST", "/api/v1/subscriptions/detect", okText = "Pemeriksaan selesai.") { reload() } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Periksa sekarang") }
        }
    }
    w.message?.let { InfoBanner(it) }
    SubsList("Aktif · ${active.size}", active, "Belum ada langganan aktif.") { acting = it }
    if (suggested.isNotEmpty()) SubsList("Usulan · ${suggested.size}", suggested, "") { acting = it }
    if (adding) {
        val fields = listOf(
            FieldSpec("name", "Nama layanan"), FieldSpec("amount", "Jumlah", Kind.Money),
            FieldSpec("period", "Periode", Kind.Choice(listOf("monthly" to "Bulanan", "yearly" to "Tahunan")), "monthly"),
            FieldSpec("next_on", "Tagihan berikutnya", Kind.Date, java.time.LocalDate.now().plusMonths(1).toString()),
        )
        FormDialog("Tambah langganan", fields, busy = w.busy, onDismiss = { adding = false }) { v ->
            w.call("POST", "/api/v1/subscriptions", bodyOf(fields, v).toString(), "Langganan ditambahkan.") { adding = false; reload() }
        }
    }
    acting?.let { s ->
        val id = s.text("id")
        val opts = if (s.text("status") == "suggested") listOf("approve" to "Setujui", "dismiss" to "Tolak") else listOf("stop" to "Hentikan langganan")
        PickDialog(s.obj("merchants")?.text("display_name") ?: "Langganan", opts, onDismiss = { acting = null }) { act ->
            acting = null
            w.call("POST", "/api/v1/subscriptions/$id/$act", okText = "Tersimpan.") { reload() }
        }
    }
}

@Composable
private fun SubsList(title: String, subs: List<JsonObject>, empty: String, onClick: (JsonObject) -> Unit) {
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        if (subs.isEmpty()) Text(empty, color = Token.teksRedup, modifier = Modifier.padding(vertical = 12.dp))
        subs.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val name = s.obj("merchants")?.text("display_name") ?: "Langganan"
            val period = when (s.text("period")) { "yearly" -> "Tahunan"; "weekly" -> "Mingguan"; else -> "Bulanan" }
            LineRow(name, "$period · berikutnya ${fmtDate(s.text("next_expected_on"))}", rupiah(money(s, "avg_amount")), onClick = { onClick(s) })
        }
    }
}

// ---------------------------------------------------------------- Piutang (F-10)

@Composable
fun ReceivablesScreen(onBack: () -> Unit) = RemoteScreen("Piutang", "/api/v1/layar/piutang", onBack, "Uang yang belum dikembalikan") { data, reload ->
    val o = data.obj() ?: return@RemoteScreen
    val open = o.list("open")
    val settled = o.list("settled")
    val accounts = o.list("accounts").filter { it.text("kind") != "credit" }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf<JsonObject?>(null) }
    GsCard {
        Text("Belum kembali", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Text(rupiah(open.sumOf { money(it, "remaining") }), style = MaterialTheme.typography.headlineSmall)
        Text("dari ${open.map { it.text("person_name") }.toSet().size} orang", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
    }
    message?.let { InfoBanner(it) }
    if (open.isEmpty()) GsCard { Text("Tidak ada piutang terbuka. Piutang muncul setelah kamu membagikan tagihan.", color = Token.teksRedup) }
    open.forEach { r ->
        GsCard {
            val split = r.obj("split_participants")
            val title = split?.obj("split_bills")?.text("title")
            Text(r.text("person_name") ?: "Teman", style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(title, "sejak ${fmtDate(r.text("created_at"))}").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            Spacer(Modifier.height(8.dp))
            Text(rupiah(money(r, "remaining")), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val phone = split?.text("phone_digits")
                OutlinedButton(onClick = {
                    val text = "Halo ${r.text("person_name")}, mengingatkan sisa ${formatRupiahPlain(money(r, "remaining"))}${title?.let { " untuk $it" } ?: ""}. Terima kasih!"
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phone.orEmpty()}?text=${Uri.encode(text)}")))
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("WhatsApp") }
                Button(onClick = { paying = r }, enabled = accounts.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Tandai lunas") }
            }
        }
    }
    paying?.let { r ->
        PickDialog(
            title = "Uang masuk ke akun mana?",
            options = accounts.map { it.text("id").orEmpty() to (it.text("name") ?: "Akun") },
            onDismiss = { paying = null },
            onPick = { accountId ->
                paying = null
                scope.launch {
                    val api = Api(Settings(ctx), SessionStore(ctx))
                    val body = buildJsonObject { put("amount", money(r, "remaining")); put("account_id", accountId) }.toString()
                    message = when (val res = api.send("POST", "/api/v1/receivables/${r.text("id")}/repayments", body)) {
                        is ApiResult.Ok -> { reload(); "Pelunasan dicatat." }
                        is ApiResult.Fail -> res.message
                        ApiResult.Offline -> "Tidak ada koneksi."
                    }
                }
            },
        )
    }
    if (settled.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Baru lunas · 30 hari", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        settled.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(r.text("person_name") ?: "Teman", "lunas ${fmtDate(r.text("settled_at"))}", rupiah(money(r, "amount")))
        }
    }
}

private fun formatRupiahPlain(n: Long) = id.glideslope.core.formatRupiah(n)

// ---------------------------------------------------------------- Rencana pengeluaran (F-06.3)

@Composable
fun PlannedScreen(onBack: () -> Unit) = RemoteScreen("Rencana pengeluaran", "/api/v1/layar/rencana", onBack, "Biaya besar yang sudah kamu tahu") { data, reload ->
    val o = data.obj() ?: return@RemoteScreen
    val today = o.text("today") ?: ""
    val occ = o.list("occurrences").filter { !it.flag("paid") && (it.text("date") ?: "") >= today }.take(12)
    val planned = o.list("planned")
    val categories = o.list("categories")
    val w = rememberWriter()
    var form by remember { mutableStateOf<JsonObject?>(null) } // an existing plan, or an empty object for a new one
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<JsonObject?>(null) }
    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Tambah rencana") }
    w.message?.let { InfoBanner(it) }
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Berikutnya", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        if (occ.isEmpty()) Text("Belum ada rencana yang akan datang.", color = Token.teksRedup, modifier = Modifier.padding(vertical = 12.dp))
        occ.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(r.text("label") ?: "Rencana", fmtDate(r.text("date")), rupiah(money(r, "amount")), onClick = {
                val id = r.text("planned_id")
                if (id != null) w.call("POST", "/api/v1/planned/$id/paid", """{"occurrence":"${r.text("date")}"}""", "Ditandai sudah dibayar.") { reload() }
            })
        }
        if (occ.isNotEmpty()) Text("Ketuk untuk menandai sudah dibayar.", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
    }
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Semua rencana · ${planned.size}", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        planned.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val every = p.long("repeat_months")?.let { "tiap $it bulan" } ?: "sekali"
            LineRow(p.text("label") ?: "Rencana", "${fmtDate(p.text("due_on"))} · $every", rupiah(money(p, "amount")), onClick = { acting = p })
        }
    }
    val catOptions = listOf("" to "Tanpa kategori") + categories.map { it.text("id").orEmpty() to (it.text("name") ?: "") }
    fun fields(p: JsonObject?) = listOf(
        FieldSpec("label", "Nama rencana", initial = p?.text("label").orEmpty()), FieldSpec("amount", "Jumlah", Kind.Money, p?.long("amount")?.toString().orEmpty()),
        FieldSpec("due_on", "Tanggal", Kind.Date, p?.text("due_on") ?: java.time.LocalDate.now().plusMonths(1).toString()),
        FieldSpec("repeat_months", "Ulangi tiap … bulan (1–12)", Kind.Number, p?.long("repeat_months")?.toString().orEmpty(), "Kosongkan bila sekali"),
        FieldSpec("repeat_until", "Ulangi sampai", Kind.Date, p?.text("repeat_until").orEmpty()),
        FieldSpec("category_id", "Kategori", Kind.Choice(catOptions), p?.text("category_id").orEmpty()),
    )
    if (adding || form != null) {
        val p = form
        val fs = fields(p)
        FormDialog(if (p == null) "Tambah rencana" else "Ubah rencana", fs, busy = w.busy, onDismiss = { adding = false; form = null }) { v ->
            w.call(if (p == null) "POST" else "PATCH", if (p == null) "/api/v1/planned" else "/api/v1/planned/${p.text("id")}", bodyOf(fs, v).toString(), "Rencana disimpan.") { adding = false; form = null; reload() }
        }
    }
    acting?.let { p ->
        PickDialog(p.text("label") ?: "Rencana", listOf("ubah" to "Ubah", "hapus" to "Hapus semua"), onDismiss = { acting = null }) { act ->
            acting = null
            if (act == "ubah") form = p else w.call("DELETE", "/api/v1/planned/${p.text("id")}", okText = "Rencana dihapus.") { reload() }
        }
    }
}

// ---------------------------------------------------------------- Label (F-11.3)

@Composable
fun LabelsScreen(onBack: () -> Unit) = RemoteScreen("Label", "/api/v1/layar/label", onBack, "Kelompokkan pengeluaran sesukamu") { data, reload ->
    val labels = data.obj()?.list("labels").orEmpty()
    val w = rememberWriter()
    var form by remember { mutableStateOf<JsonObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<JsonObject?>(null) }
    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Label baru") }
    w.message?.let { InfoBanner(it) }
    if (labels.isEmpty()) GsCard { Text("Belum ada label.", color = Token.teksRedup) }
    labels.forEach { l ->
        val spent = l.list("transaction_labels").map { it.obj("transactions") }.filter {
            it != null && it.text("kind") == "expense" && it.text("status") != "ignored" && it.text("deleted_at") == null
        }.sumOf { money(it!!, "amount") }
        val target = l.long("target_amount")
        GsCard(onClick = { acting = l }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(l.text("name") ?: "Label", style = MaterialTheme.typography.titleMedium)
                Text(if (l.text("archived_at") != null) "Diarsipkan" else if (l.flag("auto_one_off")) "Sekali jalan" else "", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup)
            }
            Spacer(Modifier.height(4.dp))
            Text(rupiah(spent), style = MaterialTheme.typography.titleLarge)
            if (target != null && target > 0) {
                Spacer(Modifier.height(8.dp))
                Meter(spent.toDouble() / target, if (spent > target) Token.galat else Token.aksenTeks)
                Text("Target ${rupiah(target)}", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            }
        }
    }
    if (adding || form != null) {
        val l = form
        val fs = listOf(
            FieldSpec("name", "Nama label", initial = l?.text("name").orEmpty()), FieldSpec("auto_one_off", "Tandai pengeluarannya sekali jalan", Kind.Toggle, if (l?.flag("auto_one_off") == true) "true" else "false"),
            FieldSpec("target_amount", "Target (opsional)", Kind.Money, l?.long("target_amount")?.toString().orEmpty()),
        )
        FormDialog(if (l == null) "Label baru" else "Ubah label", fs, busy = w.busy, onDismiss = { adding = false; form = null }) { v ->
            w.call(if (l == null) "POST" else "PATCH", if (l == null) "/api/v1/labels" else "/api/v1/labels/${l.text("id")}", bodyOf(fs, v).toString(), "Label disimpan.") { adding = false; form = null; reload() }
        }
    }
    acting?.let { l ->
        val archived = l.text("archived_at") != null
        PickDialog(l.text("name") ?: "Label", listOf("ubah" to "Ubah", "arsip" to if (archived) "Pulihkan" else "Arsipkan"), onDismiss = { acting = null }) { act ->
            acting = null
            if (act == "ubah") form = l else w.call("POST", "/api/v1/labels/${l.text("id")}/archive", """{"archive":${!archived}}""", "Tersimpan.") { reload() }
        }
    }
}

// ---------------------------------------------------------------- Analitik (F-08)

@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    RemoteScreen("Analitik", "/api/v1/layar/analitik?tab=${if (tab == 0) "pengeluaran" else "kekayaan"}", onBack) { data, _ ->
        Segmented(listOf("Pengeluaran", "Kekayaan"), tab) { tab = it }
        val o = data.obj() ?: return@RemoteScreen
        if (tab == 0) o.obj("pengeluaran")?.let { SpendingTab(it) } else o.obj("kekayaan")?.let { WealthTab(it) }
    }
}

@Composable
private fun SpendingTab(p: JsonObject) {
    val names = categoryNames(p.list("categories"))
    val bars = p.list("bars")
    GsCard {
        Text("Rata-rata per bulan", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Text(p.long("average")?.let { rupiah(it) } ?: "–", style = MaterialTheme.typography.headlineSmall)
        if (bars.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val max = bars.maxOf { money(it, "total") }.coerceAtLeast(1)
            val routine = Token.aksen
            val oneOff = Token.aksenSedang
            val track = Token.isian
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val gap = 10.dp.toPx()
                val w = (size.width - gap * (bars.size - 1)) / bars.size
                bars.forEachIndexed { i, b ->
                    val x = i * (w + gap)
                    drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(6.dp.toPx()))
                    val hr = size.height * money(b, "routine") / max
                    val ho = size.height * money(b, "oneOff") / max
                    drawRoundRect(routine, Offset(x, size.height - hr), Size(w, hr), CornerRadius(6.dp.toPx()))
                    drawRoundRect(oneOff, Offset(x, size.height - hr - ho), Size(w, ho), CornerRadius(6.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                bars.forEach { Text((it.text("month") ?: "").takeLast(2), style = MaterialTheme.typography.labelSmall, color = Token.teksRedup) }
            }
        }
    }
    val breakdown = p.list("breakdown")
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Per kategori bulan ini", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        if (breakdown.isEmpty()) Text("Belum ada pengeluaran.", color = Token.teksRedup, modifier = Modifier.padding(vertical = 12.dp))
        breakdown.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(names[r.text("category_id")] ?: "Tanpa kategori", formatPct(r.dbl("share_pct") ?: 0.0), rupiah(money(r, "amount")))
        }
    }
    val large = p.list("large")
    if (large.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Pengeluaran besar", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        large.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(t.text("merchant_raw") ?: "Pengeluaran", fmtDate(t.text("occurred_at")), rupiah(money(t, "amount")))
        }
    }
}

@Composable
private fun WealthTab(k: JsonObject) {
    GsCard {
        Text("Total kekayaan", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        Text(rupiah(money(k, "cash") + money(k, "asset_total")), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Kas", rupiah(money(k, "cash")))
            Stat("Aset", rupiah(money(k, "asset_total")))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Likuid ±3 hari kerja", rupiah(money(k, "liquid")))
            Stat("Piutang", rupiah(money(k, "owed")))
        }
    }
    val rows = k.obj("ladder")?.list("rows").orEmpty()
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Alokasi", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(r.text("label") ?: r.text("key") ?: "", r.dbl("pct")?.let { formatPct(it) }, rupiah(money(r, "value")))
        }
    }
    val inflows = k.list("scheduled_inflows")
    if (inflows.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Uang masuk terjadwal", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        inflows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(r.text("name") ?: "Aset", fmtDate(r.text("date")), rupiah(money(r, "amount")))
        }
    }
}

// ---------------------------------------------------------------- Sumber email (F-04)

@Composable
fun SourcesScreen(onBack: () -> Unit) = RemoteScreen("Sumber email", "/api/v1/layar/sumber", onBack, "Dari mana transaksi otomatis masuk") { data, reload ->
    val o = data.obj() ?: return@RemoteScreen
    val ctx = LocalContext.current
    val w = rememberWriter()
    val g = o.obj("integration")
    val status = g?.text("status")
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<JsonObject?>(null) }
    var bad by remember { mutableStateOf<JsonObject?>(null) }
    GsCard {
        Text("Gmail", style = MaterialTheme.typography.titleMedium)
        Text(
            when (status) { "terhubung" -> "Terhubung · ${g.text("account_email") ?: ""}"; "perlu_hubungkan_ulang" -> "Perlu dihubungkan ulang"; else -> "Belum terhubung" },
            style = MaterialTheme.typography.bodyMedium, color = if (status == "terhubung") Token.masuk else Token.peringatan,
        )
        g?.text("last_polled_at")?.let { Text("Terakhir dibaca ${fmtDate(it)}", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup) }
        g?.text("last_error")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Token.galat) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status != "terhubung") Button(onClick = {
                // Google's consent screen needs a browser; the server's callback returns here once the owner is signed in on the web
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Settings(ctx).serverUrl.trimEnd('/') + "/api/v1/gmail/connect")))
            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Hubungkan Gmail") }
            else {
                OutlinedButton(enabled = !w.busy, onClick = { w.call("POST", "/api/v1/backfill", okText = "Pembacaan email lama dimulai.") { reload() } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Baca email lama") }
                OutlinedButton(enabled = !w.busy, onClick = { w.call("POST", "/api/v1/gmail/disconnect", okText = "Gmail diputus.") { reload() } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Putuskan") }
            }
        }
    }
    w.message?.let { InfoBanner(it) }
    val counts = o.list("counts_30d").associateBy { it.text("source_id") }
    GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Pengirim email", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        o.list("sources").forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            val c = counts[s.text("id")]
            LineRow(
                s.text("label") ?: s.text("sender_address") ?: "Sumber",
                "${s.text("sender_address")} · 30 hari: ${c?.long("ok") ?: 0} terbaca, ${c?.long("fail") ?: 0} gagal",
                if (s.flag("enabled")) "Aktif" else "Mati", if (s.flag("enabled")) Token.masuk else Token.teksRedup, onClick = { acting = s },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("+ Pengirim baru") }
    }
    val unreadable = o.list("unreadable")
    if (unreadable.isNotEmpty()) GsCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Email yang belum terbaca · ${unreadable.size}", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
        unreadable.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = Token.garis)
            LineRow(e.text("subject") ?: "(tanpa judul)", "${e.text("sender")} · ${fmtDate(e.text("received_at"))}", onClick = { bad = e })
        }
    }
    if (adding) {
        val accounts = o.list("accounts").map { it.text("id").orEmpty() to (it.text("name") ?: "Akun") }
        val fs = listOf(
            FieldSpec("sender_address", "Alamat pengirim (mis. no-reply@bank.co.id)"), FieldSpec("label", "Nama"),
            FieldSpec("default_account_id", "Akun bawaan", Kind.Choice(listOf("" to "Tidak ada") + accounts), ""),
        )
        FormDialog("Pengirim baru", fs, busy = w.busy, onDismiss = { adding = false }) { v ->
            val body = JsonObject(bodyOf(fs, v) + ("parser_id" to kotlinx.serialization.json.JsonNull))
            w.call("POST", "/api/v1/email-sources", body.toString(), "Pengirim ditambahkan.") { adding = false; reload() }
        }
    }
    acting?.let { s ->
        PickDialog(s.text("label") ?: "Pengirim", listOf("toggle" to if (s.flag("enabled")) "Matikan" else "Aktifkan"), onDismiss = { acting = null }) {
            acting = null
            w.call("POST", "/api/v1/email-sources/${s.text("id")}/toggle", """{"enabled":${!s.flag("enabled")}}""", "Tersimpan.") { reload() }
        }
    }
    bad?.let { e ->
        PickDialog(e.text("subject") ?: "Email", listOf("retry" to "Coba baca lagi", "ignore" to "Abaikan"), onDismiss = { bad = null }) { act ->
            bad = null
            w.call("POST", "/api/v1/ingest-events/${e.text("id")}/$act", okText = "Tersimpan.") { reload() }
        }
    }
}

// ---------------------------------------------------------------- a small chooser

@Composable
fun PickDialog(title: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { options.forEach { (id, name) -> LineRow(name, onClick = { onPick(id) }) } } },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
