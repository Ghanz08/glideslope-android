// A small form toolkit for the online screens: field specs → one dialog, and a Writer that sends the result to the server.
package id.glideslope.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

sealed interface Kind {
    data object Text : Kind
    data object Money : Kind
    data object Number : Kind
    data object Decimal : Kind
    data object Date : Kind
    data object Toggle : Kind
    data class Choice(val options: List<Pair<String, String>>) : Kind
}

/** [initial] is text for every kind ("true"/"false" for Toggle, an option id for Choice, yyyy-MM-dd for Date). */
class FieldSpec(val key: String, val label: String, val kind: Kind = Kind.Text, val initial: String = "", val hint: String? = null)

/** "1.250.000" / "Rp 1250000" → 1250000; empty → null. */
fun digitsToLong(s: String): Long? = s.filter { it.isDigit() }.toLongOrNull()

/** JSON for one field's text: Money/Number → number (or null when empty), Toggle → boolean, others → string (or null when empty). */
fun fieldJson(kind: Kind, v: String): JsonElement = when (kind) {
    Kind.Money, Kind.Number -> digitsToLong(v)?.let { JsonPrimitive(it) } ?: JsonNull
    Kind.Decimal -> v.replace(',', '.').toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
    Kind.Toggle -> JsonPrimitive(v == "true")
    else -> if (v.isBlank()) JsonNull else JsonPrimitive(v.trim())
}

fun bodyOf(fields: List<FieldSpec>, values: Map<String, String>): JsonObject = buildJsonObject {
    fields.forEach { f -> put(f.key, fieldJson(f.kind, values[f.key] ?: f.initial)) }
}

class Writer(private val ctx: Context, private val scope: CoroutineScope) {
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    /** One online write. [onOk] runs on success with the parsed answer; failures land in [message] (or [onFail]). */
    fun call(method: String, path: String, body: String = "{}", okText: String? = null, onFail: ((String) -> Unit)? = null, onOk: (JsonElement) -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            when (val r = Api(Settings(ctx), SessionStore(ctx)).send(method, path, body)) {
                is ApiResult.Ok -> { message = okText; onOk(r.value) }
                is ApiResult.Fail -> (onFail ?: { message = it })(r.message)
                ApiResult.Offline -> (onFail ?: { message = it })("Tidak ada koneksi. Perubahan belum tersimpan.")
            }
            busy = false
        }
    }
}

@Composable
fun rememberWriter(): Writer {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { Writer(ctx, scope) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDialog(title: String, fields: List<FieldSpec>, submit: String = "Simpan", error: String? = null, busy: Boolean = false, onDismiss: () -> Unit, onSubmit: (Map<String, String>) -> Unit) {
    val values = remember { mutableStateOf(fields.associate { it.key to it.initial }) }
    var picking by remember { mutableStateOf<FieldSpec?>(null) }
    var dating by remember { mutableStateOf<FieldSpec?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { f ->
                    val v = values.value[f.key].orEmpty()
                    fun set(x: String) { values.value = values.value + (f.key to x) }
                    when (val k = f.kind) {
                        Kind.Toggle -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = v == "true", onCheckedChange = { set(it.toString()) })
                        }
                        is Kind.Choice -> LineRow(f.label, k.options.firstOrNull { it.first == v }?.second ?: "Pilih", onClick = { picking = f })
                        Kind.Date -> LineRow(f.label, if (v.isBlank()) "Pilih tanggal" else fmtDate(v), onClick = { dating = f })
                        else -> OutlinedTextField(
                            v, { set(if (k == Kind.Money || k == Kind.Number) it.filter(Char::isDigit) else it) },
                            label = { Text(f.label) }, supportingText = f.hint?.let { h -> { Text(h) } }, singleLine = f.kind != Kind.Text || f.key.length < 0,
                            keyboardOptions = KeyboardOptions(keyboardType = if (k == Kind.Money || k == Kind.Number) KeyboardType.Number else if (k == Kind.Decimal) KeyboardType.Decimal else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let { Text(it, color = Token.galat, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSubmit(values.value) }) { Text(if (busy) "Menyimpan…" else submit) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
    picking?.let { f ->
        PickDialog(f.label, (f.kind as Kind.Choice).options, onDismiss = { picking = null }) { values.value = values.value + (f.key to it); picking = null }
    }
    dating?.let { f ->
        val start = values.value[f.key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dating = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> values.value = values.value + (f.key to Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    dating = null
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { dating = null }) { Text("Batal") } },
        ) { DatePicker(state) }
    }
}
