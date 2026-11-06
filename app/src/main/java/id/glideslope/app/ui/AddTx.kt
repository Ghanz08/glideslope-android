// S-07 Tambah (F-03.1–F-03.3): amount on a numpad, then category, account, merchant, date, note. Saved on the phone
// first and sent when online (F-14.2), so it works with no signal.
package id.glideslope.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.data.AccountEntity
import id.glideslope.app.data.CategoryEntity
import id.glideslope.app.data.Db
import id.glideslope.app.data.Writes
import id.glideslope.core.formatRupiah
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private val WIB = ZoneId.of("Asia/Jakarta")
private const val MAX = 999_999_999L

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "Kembali" },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(26.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
fun <T> ChipRow(items: List<T>, selected: (T) -> Boolean, label: (T) -> String, onPick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(items) { item ->
            val on = selected(item)
            Box(
                Modifier.heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (on) Token.aksen else Token.permukaan)
                    .border(1.dp, if (on) Token.aksen else Token.garis, CircleShape)
                    .clickable { onPick(item) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(item), style = MaterialTheme.typography.labelLarge, color = if (on) Token.permukaan else Token.teks) }
        }
    }
}

@Composable
private fun FieldLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxScreen(kind: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val dao = remember { Db.get(ctx).sync() }
    val writes = remember { Writes(ctx) }
    val scope = rememberCoroutineScope()
    val accounts by dao.activeAccounts().collectAsState(initial = emptyList())
    val categories by dao.categories(if (kind == "pemasukan") "income" else "expense").collectAsState(initial = emptyList())
    val merchants by dao.rows("merchants").collectAsState(initial = emptyList())
    val memory = remember(merchants) {
        merchants.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            .mapNotNull { m -> (m.text("display_name") ?: return@mapNotNull null).lowercase() to (m.text("default_category_id") ?: return@mapNotNull null) }.toMap()
    }
    val transfer = kind == "transfer"
    val title = when (kind) { "pemasukan" -> "Pemasukan"; "transfer" -> "Transfer antar akun"; else -> "Pengeluaran" }

    var amount by rememberSaveable { mutableLongStateOf(0L) }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("") }
    var oneOff by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now(WIB).toString()) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accounts) { if (accountId == null) accountId = accounts.firstOrNull()?.id }
    LaunchedEffect(merchant, memory) { memory[merchant.trim().lowercase()]?.let { if (categoryId == null) categoryId = it } }

    val ready = amount > 0 && accountId != null && (if (transfer) toAccountId != null && toAccountId != accountId else categoryId != null)

    Column(Modifier.fillMaxSize().background(Token.latar).imePadding()) {
        BackBar(title, onDone)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) { contentDescription = "Jumlah ${formatRupiah(amount)}" },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(if (kind == "pengeluaran") "−" else if (kind == "pemasukan") "+" else "", style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp), color = Token.teksPelan)
                Text("Rp", style = MaterialTheme.typography.titleLarge, color = Token.teksPelan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                Text(formatRupiah(amount).removePrefix("Rp "), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 52.sp), color = if (amount > 0) Token.teks else Token.teksPelan)
            }
            if (!transfer) {
                OutlinedTextField(
                    merchant, { merchant = it.take(200) }, label = { Text("Merchant atau keterangan") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                FieldLabel("Kategori")
                ChipRow(categories, { it.id == categoryId }, CategoryEntity::name) { categoryId = it.id }
            }
            FieldLabel(if (transfer) "Dari akun" else "Akun")
            ChipRow(accounts, { it.id == accountId }, AccountEntity::name) { accountId = it.id }
            if (transfer) {
                FieldLabel("Ke akun")
                ChipRow(accounts.filter { it.id != accountId }, { it.id == toAccountId }, AccountEntity::name) { toAccountId = it.id }
                OutlinedTextField(
                    fee, { fee = it.filter(Char::isDigit).take(7) }, label = { Text("Biaya admin (Rp)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Token.permukaan).border(1.dp, Token.garis, RoundedCornerShape(12.dp))
                        .clickable { picking = true }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Tanggal: ${id.glideslope.core.formatDayMonth(LocalDate.parse(date))}", style = MaterialTheme.typography.labelLarge) }
                if (kind == "pengeluaran") {
                    Spacer(Modifier.weight(1f))
                    Text("Sekali jalan", style = MaterialTheme.typography.bodyMedium)
                    Switch(oneOff, { oneOff = it })
                }
            }
            if (!transfer) OutlinedTextField(
                note, { note = it.take(500) }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            error?.let { Text(it, color = Token.galat, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        Numpad(
            onDigit = { d -> (amount * 10 + (d - '0')).let { if (it <= MAX) amount = it } },
            onTripleZero = { if (amount > 0 && amount * 1000 <= MAX) amount *= 1000 },
            onDelete = { amount /= 10 },
        )
        Button(
            enabled = ready,
            onClick = {
                val today = LocalDate.now(WIB)
                val d = LocalDate.parse(date)
                val at = if (d == today) Instant.now() else d.atTime(LocalTime.NOON).atZone(WIB).toInstant()
                scope.launch {
                    runCatching {
                        if (transfer) writes.createTransfer(accountId!!, toAccountId!!, amount, fee.toLongOrNull() ?: 0, at)
                        else writes.createTx(
                            if (kind == "pemasukan") "income" else "expense", amount, accountId!!, categoryId!!,
                            merchant.trim().ifBlank { null }, at, note.trim().ifBlank { null }, oneOff,
                        )
                    }.onSuccess {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDone()
                    }.onFailure { error = "Belum tersimpan. Coba lagi." }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Token.aksen, disabledContainerColor = Token.aksenSedang),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 56.dp),
        ) { Text("Simpan", style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp)) }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                // F-03.1: at most one day ahead
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= LocalDate.now(WIB).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Batal") } },
        ) { DatePicker(state) }
    }
}

/** PRD 4.4 numpad: 0–9, 000 and delete; keys ≥ 56 dp. */
@Composable
private fun Numpad(onDigit: (Char) -> Unit, onTripleZero: () -> Unit, onDelete: () -> Unit) {
    val view = LocalView.current
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", "⌫")
    Column(Modifier.fillMaxWidth().background(Token.permukaan).padding(horizontal = 12.dp, vertical = 8.dp).semantics { contentDescription = "Papan angka" }) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            when (k) { "⌫" -> onDelete(); "000" -> onTripleZero(); else -> onDigit(k[0]) }
                        }.semantics { contentDescription = if (k == "⌫") "Hapus angka" else k },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight(600)), color = Token.teks) }
                }
            }
        }
    }
}

