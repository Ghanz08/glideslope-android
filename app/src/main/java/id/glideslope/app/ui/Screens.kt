// Sign-in, Beranda and Transaksi (F-14, phase N2: read-only from the local cache).
package id.glideslope.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.glideslope.app.MainActivity
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.Db
import id.glideslope.app.data.SessionStore
import id.glideslope.app.data.TxListItem
import id.glideslope.app.sync.PullWorker
import id.glideslope.core.formatRupiah
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ID = Locale.forLanguageTag("id")
private val WIB = ZoneId.of("Asia/Jakarta")
private val jam = DateTimeFormatter.ofPattern("d MMM HH.mm", ID).withZone(WIB)
private val tanggal = DateTimeFormatter.ofPattern("d MMM yyyy", ID)

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })

/** S-01 v2 (F-16.21), phone layout: the mark, "Masuk ke catatan keuanganmu", labelled fields over a pill button; step 2 shows the two-bar progress and six code boxes. */
@Composable
fun AuthScreen(activity: MainActivity, onSignedIn: () -> Unit) {
    val settings = remember { Settings(activity) }
    val api = remember { Api(settings, SessionStore(activity)) }
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var needCode by rememberSaveable { mutableStateOf(SessionStore(activity).get()?.aal == "aal1") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun show(r: ApiResult<*>) {
        error = when (r) {
            is ApiResult.Fail -> r.message
            ApiResult.Offline -> "Server tidak terjangkau. Periksa koneksi lalu coba lagi."
            is ApiResult.Ok -> null
        }
    }
    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            if (!needCode) {
                val r = api.login(email.trim(), password)
                show(r)
                if (r is ApiResult.Ok) { password = ""; needCode = true }
            } else {
                val r = api.verifyCode(code)
                show(r)
                if (r is ApiResult.Ok && r.value.aal == "aal2") onSignedIn()
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(Token.latar).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 40.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Token.aksen), contentAlignment = Alignment.Center) {
            Icon(Ic.trendingDown, null, tint = Token.teksDiAksen, modifier = Modifier.size(31.dp))
        }
        Text("Masuk ke catatan keuanganmu", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp), color = Token.teks, modifier = Modifier.padding(top = 20.dp, bottom = 28.dp).semantics { heading() })
        if (!needCode) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AuthField("Email", email, { email = it }, "nama@email.com", keyboard = KeyboardType.Email)
                AuthField(
                    "Kata sandi", password, { password = it }, "", error = error, keyboard = KeyboardType.Password, hidden = !show,
                    trailing = {
                        Icon(
                            if (show) Ic.eyeOff else Ic.eye, if (show) "Sembunyikan sandi" else "Tampilkan sandi", tint = Token.teksRedup,
                            modifier = Modifier.size(44.dp).clickable { show = !show }.padding(13.dp),
                        )
                    },
                    onDone = ::submit,
                )
                if (password.isEmpty() && error == null) Text("Kata sandi wajib diisi.", style = MaterialTheme.typography.bodySmall, color = Token.teksPelan)
                PillButton(if (busy) "Memeriksa…" else "Lanjut", enabled = password.isNotEmpty() && email.isNotBlank() && !busy, onClick = ::submit)
                Text("Setelah ini: kode 6 angka dari Google Authenticator / 1Password.", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Langkah 2 dari 2 · kode dari aplikasi autentikator", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) { Box(Modifier.width(56.dp).height(4.dp).clip(CircleShape).background(Token.aksen)) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kode 6 angka dari autentikator", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = Token.teksRedup)
                    OtpBoxes(code, { code = it; if (it.length == 6 && !busy) submit() }, error != null)
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Token.galat, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }
                Text("Kode berubah tiap 30 detik. Perangkat hilang? Lihat panduan pemulihan di README.", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                PillButton(if (busy) "Memeriksa…" else "Verifikasi", enabled = code.length == 6 && !busy, onClick = ::submit)
                TextButton(onClick = { needCode = false; code = ""; error = null; SessionStore(activity).set(null) }) { Text("Pakai akun lain", color = Token.aksenTeks) }
            }
        }
    }
}

/** A labelled input as the PWA draws it: 13 sp semibold label, 12 dp corners, 1 dp border (red on error). */
@Composable
private fun AuthField(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String, error: String? = null, keyboard: KeyboardType = KeyboardType.Text,
    hidden: Boolean = false, trailing: (@Composable () -> Unit)? = null, onDone: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = Token.teksRedup)
        androidx.compose.foundation.text.BasicTextField(
            value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Token.teks),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Token.aksen),
            visualTransformation = if (hidden) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Token.permukaan)
                        .border(1.dp, if (error != null) Token.galat else Token.garisKontrol, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = if (trailing != null) 2.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Token.teksPelan); inner() }
                    trailing?.invoke()
                }
            },
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Token.galat, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

/** Six boxes over one real input (paste and SMS autofill keep working); the active box gets the accent border and ring. */
@Composable
private fun OtpBoxes(code: String, onChange: (String) -> Unit, error: Boolean) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.foundation.text.BasicTextField(
        code, { onChange(it.filter(Char::isDigit).take(6)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent), cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Kode 6 angka dari autentikator" },
        decorationBox = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 6) {
                    if (i == 3) Text("-", color = Token.teksPelan)
                    val active = i == minOf(code.length, 5)
                    Box(
                        Modifier.width(46.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Token.permukaan)
                            .border(if (active && !error) 2.dp else 1.dp, if (error) Token.galat else if (active) Token.aksen else Token.garisKontrol, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(code.getOrNull(i)?.toString() ?: "", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Token.teks) }
                }
            }
        },
    )
}

/** The full-width pill: accent fill, 48 dp, faded when disabled (as the PWA's Button). */
@Composable
private fun PillButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).background(Token.aksen.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Token.teksDiAksen.copy(alpha = if (enabled) 1f else 0.85f)) }
}
