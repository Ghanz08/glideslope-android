// S-08 Transaksi and S-09 detail (F-03.4): grouped by day with day totals, search and kind filters; the detail
// shows evidence and lets the owner edit, mark one-off, delete (with undo) or restore. Edits go through F-14.3.
package id.glideslope.app.ui

import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import id.glideslope.app.sync.PullWorker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.glideslope.app.data.AccountEntity
import id.glideslope.app.data.CategoryEntity
import id.glideslope.app.data.Db
import id.glideslope.app.data.TxEntity
import id.glideslope.app.data.TxListItem
import id.glideslope.app.data.Writes
import id.glideslope.core.formatLongDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WIB = ZoneId.of("Asia/Jakarta")
private val hm = DateTimeFormatter.ofPattern("HH.mm", Locale.forLanguageTag("id"))
private fun wib(at: String) = runCatching { OffsetDateTime.parse(at).atZoneSameInstant(WIB) }.getOrNull()

private val FILTERS = listOf("Semua", "Pengeluaran", "Pemasukan", "Transfer", "Perlu dicek")

@Composable
fun TransactionsScreen(pad: PaddingValues, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val items by remember { Db.get(ctx).sync().recent(1000) }.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    // category name → tone_key from the synced categories, for the coloured tiles (the typed table keeps only id/name/kind)
    val catRows by remember { Db.get(ctx).sync().rows("categories") }.collectAsState(initial = emptyList())
    val tones = remember(catRows) { catRows.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }.associate { (it.text("name") ?: "") to it.text("tone_key") } }
    val shown = remember(items, query, filter) { items.filter { t ->
        when (filter) {
            1 -> t.kind == "expense" || t.kind == "fee"
            2 -> t.direction == "in" && t.kind != "transfer"
            3 -> t.kind == "transfer"
            4 -> t.status == "needs_review"
            else -> true
        } && (query.isBlank() || listOfNotNull(t.merchantRaw, t.note, t.categoryName, t.accountName).any { it.contains(query.trim(), ignoreCase = true) })
    } }
    val days = remember(shown) { shown.groupBy { wib(it.occurredAt)?.toLocalDate() ?: LocalDate.MIN } }
    LazyColumn(Modifier.fillMaxSize().padding(pad).statusBarsPadding(), contentPadding = PaddingValues(bottom = barClearance())) {
        item { Box(Modifier.padding(16.dp)) { PageTitle("Transaksi") } }
        item {
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Cari merchant, catatan, kategori") }, singleLine = true,
                leadingIcon = { Icon(Ic.list, null, tint = Token.teksRedup) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            ChipRow(FILTERS.indices.toList(), { it == filter }, { FILTERS[it] }) { filter = it }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) item { Text("Tidak ada transaksi.", color = Token.teksRedup, modifier = Modifier.padding(16.dp)) }
        days.forEach { (day, list) ->
            item(key = "d$day") {
                val net = list.filter { it.kind != "transfer" }.sumOf { if (it.direction == "in") it.amount else -it.amount }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
                    Text(if (day == LocalDate.MIN) "Tanpa tanggal" else formatLongDate(day), style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.weight(1f))
                    Text((if (net >= 0) "+" else "−") + rupiah(kotlin.math.abs(net)), style = MaterialTheme.typography.labelMedium, color = Token.teksRedup)
                }
            }
            item(key = "c$day") {
                GsCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
                    list.forEachIndexed { i, t ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 76.dp).height(1.dp).background(Token.garis))
                        TxLine(t, tones[t.categoryName]) { onOpen(t.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxLine(t: TxListItem, toneKey: String?, onClick: () -> Unit) {
    val incoming = t.direction == "in"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (t.kind == "transfer" || t.categoryName == null) IconTile(
            if (t.kind == "transfer") Ic.arrows else if (incoming) Ic.arrowDownLeft else Ic.arrowUpRight,
            bg = if (incoming) Token.masukLatar else Token.isian, fg = if (incoming) Token.masuk else Token.teks, size = 44.dp,
        ) else {
            // the category's tone, as the PWA's CategoryTile (an initial stands in for the icon)
            val (bg, fg) = Token.tone(toneKey)
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(t.categoryName.take(1).uppercase(), color = fg, style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(t.merchantRaw ?: t.categoryName ?: "Tanpa keterangan", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(wib(t.occurredAt)?.format(hm), t.categoryName, t.accountName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Token.teksRedup, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when {
                t.pending -> Text("Menunggu dikirim", style = MaterialTheme.typography.labelSmall, color = Token.teksPelan)
                t.status == "needs_review" -> Text("Perlu dicek", style = MaterialTheme.typography.labelSmall, color = Token.peringatan)
            }
        }
        Text((if (incoming) "+" else "−") + rupiah(t.amount), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), color = if (incoming) Token.masuk else Token.teks)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDetailScreen(id: String, onBack: () -> Unit, onSplit: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).sync() }
    val writes = remember { Writes(ctx) }
    val scope = rememberCoroutineScope()
    val tx by remember(id) { dao.txFlow(id) }.collectAsState(initial = null)
    val accounts by dao.allAccounts().collectAsState(initial = emptyList())
    val categories by dao.allCategories().collectAsState(initial = emptyList())
    val labelRows by dao.rows("labels").collectAsState(initial = emptyList())
    val linkRows by remember(id) { dao.rowsMentioning("transaction_labels", id) }.collectAsState(initial = emptyList())
    val evidenceRows by remember(id) { dao.rowsMentioning("transaction_evidence", id) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(false) }
    var lending by remember { mutableStateOf(false) }
    var lendError by remember { mutableStateOf<String?>(null) }
    val t = tx ?: run { Column(Modifier.fillMaxSize()) { BackBar("Transaksi", onBack) }; return }
    val json = { s: String -> runCatching { Json.parseToJsonElement(s).jsonObject }.getOrNull() }
    val labels = remember(labelRows, linkRows, id) {
        val names = labelRows.mapNotNull(json).associate { (it.text("id") ?: "") to (it.text("name") ?: "") }
        linkRows.mapNotNull(json).filter { it.text("transaction_id") == id }.mapNotNull { names[it.text("label_id")] }
    }
    val evidence = remember(evidenceRows, id) { evidenceRows.mapNotNull(json).filter { it.text("transaction_id") == id } }
    val acc = accounts.firstOrNull { it.id == t.accountId }
    val cat = categories.firstOrNull { it.id == t.categoryId }
    val at = wib(t.occurredAt)

    Column(Modifier.fillMaxSize().background(Token.latar)) {
        BackBar("Transaksi", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GsCard {
                Text(t.merchantRaw ?: cat?.name ?: "Transaksi", style = MaterialTheme.typography.titleLarge)
                Text(at?.let { formatLongDate(it.toLocalDate()) + " · " + it.format(hm) } ?: "", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Text(if (t.direction == "in") "+" else "−", style = MaterialTheme.typography.displaySmall, color = if (t.direction == "in") Token.masuk else Token.teks)
                    Text("Rp", style = MaterialTheme.typography.titleLarge, color = Token.teksPelan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    Text(rupiah(t.amount).removePrefix("Rp "), style = MaterialTheme.typography.displaySmall)
                }
                when {
                    t.deletedAt != null -> Pill("Dihapus", Ic.x, Token.galatLatar, Token.galat)
                    t.pending -> Pill("Menunggu dikirim", null, Token.isian, Token.teksRedup)
                    t.status == "needs_review" -> Pill("Perlu dicek", Ic.triangleAlert, Token.peringatanLatar, Token.peringatan)
                    else -> Pill("Terkonfirmasi", Ic.circleCheck, Token.aksenLembut, Token.aksenTeks)
                }
                DetailRow("Akun", acc?.name ?: "—", if (t.deletedAt == null && t.kind != "transfer") ({ editing = true }) else null)
                if (t.deletedAt == null && !t.pending && (t.kind == "expense" || t.kind == "income")) {
                    DetailRow(if (t.direction == "out") "Pinjamkan ke…" else "Pinjam dari…", "Bukan " + (if (t.direction == "out") "pengeluaran" else "pemasukan"), { lending = true })
                }
                if (t.kind != "transfer") DetailRow("Kategori", cat?.name ?: "—", if (t.deletedAt == null) ({ editing = true }) else null)
                t.note?.let { DetailRow("Catatan", it) }
                if (labels.isNotEmpty()) DetailRow("Label", labels.joinToString(", "))
                if (evidence.isNotEmpty()) DetailRow("Bukti", evidence.joinToString(" + ") { e -> when (e.text("kind")) { "email" -> "Email"; "receipt" -> "Nota"; "webhook" -> "Notifikasi HP"; else -> "Manual" } })
                if (t.kind == "expense") Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sekali jalan", style = MaterialTheme.typography.bodyLarge)
                        Text("Tidak dihitung dalam rata-rata bulanan", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                    }
                    Switch(t.isOneOff, { v -> scope.launch { writes.editTx(t, buildJsonObject { put("is_one_off", v) }, t.copy(isOneOff = v)) } }, enabled = t.deletedAt == null)
                }
            }
            if (t.deletedAt == null) {
                if (t.kind != "transfer") OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ubah") }
                if (t.kind == "expense" && !t.pending) {
                    val sw = rememberWriter()
                    sw.message?.let { InfoBanner(it, warning = true) }
                    OutlinedButton(enabled = !sw.busy, onClick = { startSplitFromTransaction(sw, t.id, onSplit) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Bagi tagihan") }
                }
                OutlinedButton(
                    onClick = { scope.launch { writes.deleteTx(t) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "Hapus transaksi" },
                ) { Text("Hapus", color = Token.galat) }
            } else {
                Button(onClick = { scope.launch { writes.restoreTx(t) } }, colors = ButtonDefaults.buttonColors(containerColor = Token.aksen), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Pulihkan") }
            }
        }
    }
    if (lending) {
        val out = t.direction == "out"
        val fs = listOf(
            FieldSpec("name", if (out) "Dipinjamkan ke" else "Dipinjam dari", initial = t.merchantRaw.orEmpty()),
            FieldSpec("note", "Kesepakatan (opsional)", hint = "Untuk apa, kapan kembali"),
            FieldSpec("due_on", "Tanggal kembali (opsional)", Kind.Date, ""),
        )
        FormDialog(if (out) "Pinjamkan ke…" else "Pinjam dari…", fs, submit = "Catat", error = lendError, onDismiss = { lending = false; lendError = null }) { v ->
            val body = kotlinx.serialization.json.buildJsonObject {
                put("transaction_id", id); put("direction", if (out) "piutang" else "utang"); put("contact_name", (v["name"] ?: "").trim())
                (v["note"] ?: "").trim().takeIf { it.isNotEmpty() }?.let { put("note", it) }
                (v["due_on"] ?: "").takeIf { it.isNotBlank() }?.let { put("due_on", it) }
            }
            scope.launch {
                when (val r = Api(Settings(ctx), SessionStore(ctx)).send("POST", "/api/v1/loans", body.toString())) {
                    is ApiResult.Ok -> { lending = false; lendError = null; PullWorker.now(ctx) }
                    is ApiResult.Fail -> lendError = r.message
                    ApiResult.Offline -> lendError = "Tidak ada koneksi. Pinjaman dicatat saat online."
                }
            }
        }
    }
    if (editing) ModalBottomSheet(onDismissRequest = { editing = false }, containerColor = Token.permukaan) {
        EditTx(t, accounts.filter { !it.isSystem && it.archivedAt == null }, categories.filter { it.kind == (if (t.direction == "in") "income" else "expense") }) { patch, updated ->
            editing = false
            if (patch.isNotEmpty()) scope.launch { writes.editTx(t, patch, updated) }
        }
    }
}

@Composable
private fun EditTx(t: TxEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>, onSave: (kotlinx.serialization.json.JsonObject, TxEntity) -> Unit) {
    var amount by remember { mutableStateOf(t.amount.toString()) }
    var merchant by remember { mutableStateOf(t.merchantRaw ?: "") }
    var note by remember { mutableStateOf(t.note ?: "") }
    var acc by remember { mutableStateOf(t.accountId) }
    var cat by remember { mutableStateOf(t.categoryId) }
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ubah transaksi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(9) }, label = { Text("Jumlah (Rp)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        OutlinedTextField(merchant, { merchant = it.take(200) }, label = { Text("Merchant atau keterangan") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Text("Akun", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 16.dp))
        ChipRow(accounts, { it.id == acc }, AccountEntity::name) { acc = it.id }
        Text("Kategori", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 16.dp))
        ChipRow(categories, { it.id == cat }, CategoryEntity::name) { cat = it.id }
        OutlinedTextField(note, { note = it.take(500) }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        val newAmount = amount.toLongOrNull() ?: 0
        Button(
            enabled = newAmount in 1..999_999_999,
            onClick = {
                val m = merchant.trim().ifBlank { null }
                val n = note.trim().ifBlank { null }
                val patch = buildJsonObject {
                    if (newAmount != t.amount) put("amount", newAmount)
                    if (acc != t.accountId) put("account_id", acc)
                    if (cat != t.categoryId) put("category_id", cat)
                    if (m != t.merchantRaw) put("merchant_raw", m)
                    if (n != t.note) put("note", n)
                }
                onSave(patch, t.copy(amount = newAmount, accountId = acc, categoryId = cat, merchantRaw = m, note = n))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Token.aksen),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
        ) { Text("Simpan") }
    }
}

