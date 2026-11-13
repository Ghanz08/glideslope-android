// S-05 Inbox (F-05): one card at a time. Swipe right or tap Konfirmasi to confirm, swipe left to skip for now,
// Ubah to pick category/account, Abaikan to ignore, or turn a top-up into a transfer. Works offline (F-14.2).
package id.glideslope.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.data.AccountEntity
import id.glideslope.app.data.CategoryEntity
import id.glideslope.app.data.Db
import id.glideslope.app.data.TxEntity
import id.glideslope.app.data.Writes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val WIB = ZoneId.of("Asia/Jakarta")
private val cardTime = DateTimeFormatter.ofPattern("EEEE, d MMM · HH.mm", Locale.forLanguageTag("id"))

private val REASONS = mapOf(
    "merchant_baru" to "Merchant baru: pilih kategori sekali, berikutnya otomatis",
    "kepercayaan_rendah" to "Belum yakin dengan hasil bacaan",
    "nominal_besar" to "Nominal besar, periksa dulu",
    "transfer_internal" to "Sepertinya pindah uang ke akunmu sendiri",
    "akun_tidak_dikenal" to "Akun belum dikenali",
    "duplikat_diduga" to "Mungkin tercatat dua kali",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(pad: PaddingValues) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val dao = remember { Db.get(ctx).sync() }
    val writes = remember { Writes(ctx) }
    val scope = rememberCoroutineScope()
    val queue by dao.reviewQueue().collectAsState(initial = emptyList())
    val accounts by dao.allAccounts().collectAsState(initial = emptyList())
    val categories by dao.allCategories().collectAsState(initial = emptyList())
    val queueIds = queue.map { it.id }
    val rows by remember(queueIds) { dao.rowsByKey("transactions", queueIds) }.collectAsState(initial = emptyList())
    val problems by dao.outboxProblems().collectAsState(initial = emptyList())
    val waiting by dao.outboxCount().collectAsState(initial = 0)
    var skipped by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<TxEntity?>(null) }
    var total by remember { mutableIntStateOf(0) }
    val visible = queue.filter { it.id !in skipped }
    if (visible.size > total) total = visible.size
    val done = (total - visible.size).coerceAtLeast(0)
    val reasonOf = remember(rows) {
        rows.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            .associate { (it.text("id") ?: "") to (it.text("review_reason") ?: "") }
    }
    val current = visible.firstOrNull()
    val next = visible.getOrNull(1)
    val accountOf = accounts.associateBy { it.id }
    val categoryOf = categories.associateBy { it.id }

    fun confirm(tx: TxEntity, cat: String? = tx.categoryId, acc: String? = tx.accountId) {
        val needsCategory = tx.kind == "expense" && cat == null
        val needsAccount = accountOf[acc]?.isSystem != false
        if (needsCategory || needsAccount) { editing = tx; return }
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        scope.launch { writes.confirmReview(tx, cat, acc) }
    }

    Column(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Inbox", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (total > 0 && current != null) Text("${done + 1} dari $total", style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup)
        }
        // F-14.2: refusals and conflicts are never dropped silently
        problems.forEach { p ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                InfoBanner(
                    (if (p.status == "konflik") "Konflik: " else "Ditolak server: ") + (p.error ?: "Perubahan belum bisa diterapkan."),
                    warning = true, action = "Buang perubahan ini", onAction = { scope.launch { dao.outboxDone(p.opId); id.glideslope.app.sync.PullWorker.now(ctx) } },
                )
            }
        }
        if (waiting > 0) Text("$waiting perubahan menunggu dikirim", style = MaterialTheme.typography.bodySmall, color = Token.teksPelan, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (total > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total.coerceAtMost(12)) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < done) Token.aksenSedang else if (i == done) Token.aksen else Token.garis))
            }
        }
        if (current == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Ic.circleCheck, null, tint = Token.masuk, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text("Semua sudah ditinjau", style = MaterialTheme.typography.titleLarge)
                Text("Transaksi baru dari email dan notifikasi akan muncul di sini.", color = Token.teksRedup, modifier = Modifier.padding(top = 8.dp))
                if (skipped.isNotEmpty()) Text(
                    "Tampilkan lagi ${skipped.size} yang dilewati", color = Token.aksenTeks, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 24.dp).clickable { skipped = emptySet(); total = 0 }.padding(12.dp),
                )
            }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SwipeCard(
                key = current.id,
                onConfirm = { confirm(current) },
                onSkip = { skipped = skipped + current.id },
            ) {
                ReviewDetails(current, accountOf[current.accountId], categoryOf[current.categoryId], reasonOf[current.id], onEdit = { editing = current })
            }
            next?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)).background(Token.permukaan.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Berikutnya · ${it.merchantRaw ?: "Transaksi"}", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (it.direction == "in") "+" else "−") + rupiah(it.amount), style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = { confirm(current) },
                colors = ButtonDefaults.buttonColors(containerColor = Token.aksen),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Konfirmasi", style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp)) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextAction("Ubah", Modifier.weight(1f)) { editing = current }
                TextAction("Lewati", Modifier.weight(1f)) { skipped = skipped + current.id }
                TextAction("Abaikan", Modifier.weight(1f)) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    scope.launch { writes.moveReview(current, "ignore") }
                }
            }
        }
    }

    editing?.let { tx ->
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = Token.permukaan) {
            EditReview(
                tx, accounts.filter { !it.isSystem && it.archivedAt == null },
                categories.filter { it.kind == (if (tx.direction == "in") "income" else "expense") },
                onConfirm = { cat, acc -> editing = null; confirm(tx, cat, acc) },
                onTransfer = { to -> editing = null; scope.launch { writes.convertToTransfer(tx, to) } },
            )
        }
    }
}

@Composable
private fun TextAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = Token.teksRedup)
    }
}

/** Drag past 80 dp to act (PRD 4.4); the same actions are exposed to TalkBack as custom actions. */
@Composable
private fun SwipeCard(key: String, onConfirm: () -> Unit, onSkip: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember(key) { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .rotate(offset.value / 60f)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { d -> scope.launch { offset.snapTo(offset.value + d) } },
                onDragStopped = {
                    val v = offset.value
                    when {
                        v > threshold -> { offset.animateTo(1200f); onConfirm() }
                        v < -threshold -> { offset.animateTo(-1200f); onSkip() }
                    }
                    // the card comes back unless the item left the queue (a new key gives a fresh card);
                    // e.g. a confirm that first needs a category opens the sheet with the card in place
                    offset.animateTo(0f)
                },
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Konfirmasi") { onConfirm(); true },
                    CustomAccessibilityAction("Lewati") { onSkip(); true },
                )
            },
    ) {
        GsCard(Modifier.fillMaxWidth()) { content() }
        if (abs(offset.value) > 24) {
            val confirm = offset.value > 0
            Box(
                Modifier.align(if (confirm) Alignment.TopStart else Alignment.TopEnd).padding(16.dp).clip(CircleShape)
                    .background(if (confirm) Token.aksen else Token.teksRedup).padding(horizontal = 14.dp, vertical = 6.dp),
            ) { Text(if (confirm) "Konfirmasi" else "Lewati", color = Token.teksDiAksen, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ReviewDetails(tx: TxEntity, account: AccountEntity?, category: CategoryEntity?, reason: String?, onEdit: () -> Unit) {
    val time = runCatching { cardTime.format(OffsetDateTime.parse(tx.occurredAt).atZoneSameInstant(WIB)) }.getOrDefault("")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconTile(if (tx.direction == "in") Ic.arrowDownLeft else Ic.arrowUpRight, bg = Token.aksenLembut, fg = Token.aksenTeks, size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(tx.merchantRaw ?: "Transaksi", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(time, account?.takeIf { !it.isSystem }?.name).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        }
    }
    Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.Bottom) {
        Text(if (tx.direction == "in") "+" else "−", style = MaterialTheme.typography.displaySmall, color = Token.teks)
        Text("Rp", style = MaterialTheme.typography.titleLarge, color = Token.teksPelan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        Text(rupiah(tx.amount).removePrefix("Rp "), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 52.sp))
    }
    reason?.let { REASONS[it] }?.let {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Token.peringatanLatar).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.info, null, tint = Token.peringatan, modifier = Modifier.size(18.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Token.peringatan)
        }
    }
    DetailRow("Akun", account?.takeIf { !it.isSystem }?.name ?: "Pilih akun", onEdit)
    if (tx.kind != "transfer") DetailRow("Kategori", category?.name ?: "Pilih kategori", onEdit)
}

@Composable
fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    HorizontalDivider(color = Token.garis, modifier = Modifier.padding(top = 12.dp))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup, modifier = Modifier.size(width = 96.dp, height = 24.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Ic.chevronRight, null, tint = Token.teksRedup, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EditReview(
    tx: TxEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>,
    onConfirm: (String?, String?) -> Unit, onTransfer: (String) -> Unit,
) {
    var cat by remember { mutableStateOf(tx.categoryId) }
    var acc by remember { mutableStateOf(tx.accountId.takeIf { id -> accounts.any { it.id == id } }) }
    var asTransfer by remember { mutableStateOf(false) }
    var to by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ubah sebelum konfirmasi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Text("Akun", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 16.dp))
        ChipRow(accounts, { it.id == acc }, AccountEntity::name) { acc = it.id }
        if (tx.direction == "out" && tx.kind == "expense") {
            Box(Modifier.padding(horizontal = 16.dp)) { Segmented(listOf("Pengeluaran", "Pindah ke akunku"), if (asTransfer) 1 else 0) { asTransfer = it == 1 } }
        }
        if (asTransfer) {
            Text("Ke akun", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 16.dp))
            ChipRow(accounts.filter { it.id != acc }, { it.id == to }, AccountEntity::name) { to = it.id }
        } else if (tx.kind != "transfer") {
            Text("Kategori", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 16.dp))
            ChipRow(categories, { it.id == cat }, CategoryEntity::name) { cat = it.id }
        }
        Button(
            enabled = acc != null && (if (asTransfer) to != null else tx.kind == "transfer" || cat != null || tx.direction == "in"),
            onClick = { if (asTransfer) onTransfer(to!!) else onConfirm(cat, acc) },
            colors = ButtonDefaults.buttonColors(containerColor = Token.aksen),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
        ) { Text(if (asTransfer) "Jadikan transfer" else "Konfirmasi") }
    }
}
