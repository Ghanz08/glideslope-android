// F-01.2: the lock screen. A fingerprint prompt opens by itself; "Gunakan PIN" (or a locked-out fingerprint) turns the screen into a
// PIN pad like a bank app's. The server checks the PIN and counts the wrong ones; the phone never holds it.
package id.glideslope.app.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.AppLock
import id.glideslope.app.Boot
import id.glideslope.app.MainActivity
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.Db
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Blue = Color(0xFF2D5BD6)
private val ErrorOnBlue = Color(0xFFFFC9C2) // readable on the brand blue, unlike the app's red

@Composable
fun LockScreen(activity: MainActivity, onUnlocked: () -> Unit, onSessionEnded: () -> Unit) {
    val pinOn = remember { AppLock.pinEnabled(activity) }
    val biometric = remember { AppLock.canBiometric(activity) }
    var pinMode by remember { mutableStateOf(false) }

    fun fingerprint() = AppLock.promptBiometric(activity, pinOn, onUnlocked) { pinMode = true }
    // the fingerprint if there is one, else the PIN, else (no PIN yet, no fingerprint) the phone's screen lock
    fun start() = when {
        biometric -> fingerprint()
        pinOn -> pinMode = true
        else -> AppLock.promptCredential(activity, onUnlocked)
    }

    Box(Modifier.fillMaxSize().background(Blue)) {
        Crossfade(pinMode, animationSpec = tween(220), label = "lock") { pin ->
            if (pin) {
                BackHandler(enabled = biometric) { pinMode = false }
                PinPad(activity, biometric, onFingerprint = ::fingerprint, onUnlocked = onUnlocked, onSessionEnded = onSessionEnded)
            } else {
                Intro(pinOn, onStart = ::start, onUsePin = { pinMode = true })
            }
        }
    }
}

/**
 * The same blue as the splash, so the splash fades away and the content rises in one piece after another; the glide path draws
 * itself last and the prompt comes after all of it.
 */
@Composable
private fun Intro(pinOn: Boolean, onStart: () -> Unit, onUsePin: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    val pulse = rememberInfiniteTransition(label = "dot").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse")
    LaunchedEffect(Boot.released) {
        if (!Boot.released) return@LaunchedEffect
        launch { appear.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { delay(450); draw.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
        delay(1100) // the prompt waits until the screen has settled
        onStart()
    }
    // item i starts a little after item i-1: 0 → 1 over about 60 % of the time
    fun Modifier.rise(i: Int) = graphicsLayer {
        val e = ((appear.value - i * 0.1f) / 0.6f).coerceIn(0f, 1f)
        alpha = e
        translationY = (1f - e) * 28.dp.toPx()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        Row(Modifier.padding(top = 28.dp).rise(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GsLogo(44.dp, bg = Color.White, fg = Blue)
            Text("Glideslope", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Terbang mulus sampai wisuda.", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 41.sp), fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.rise(1))
            Row(Modifier.padding(top = 16.dp).rise(2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ic.lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Text("Terkunci. Buka dengan sidik jari" + if (pinOn) " atau PIN." else ".", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
            }
        }
        GlideLine(Color.White, draw.value, pulse.value, Modifier.fillMaxWidth().height(120.dp).padding(end = 4.dp))
        Box(
            Modifier.padding(top = 28.dp).rise(3).fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(Color.White).clickable(onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.fingerprint, null, tint = Blue, modifier = Modifier.size(24.dp))
                Text("Buka kunci", style = MaterialTheme.typography.titleMedium, color = Blue)
            }
        }
        // a PIN on: the way to it is always on screen; without one, the owner is told where to set it
        Box(Modifier.padding(top = 4.dp, bottom = 24.dp).rise(3).fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (pinOn) Text("Gunakan PIN", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = Color.White, modifier = Modifier.clip(CircleShape).clickable(onClick = onUsePin).padding(horizontal = 20.dp, vertical = 12.dp))
            else Text("Atur PIN di Pengaturan sebagai cadangan sidik jari.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun PinPad(activity: MainActivity, biometric: Boolean, onFingerprint: () -> Unit, onUnlocked: () -> Unit, onSessionEnded: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dao = remember { Db.get(activity).sync() }
    val waiting by dao.outboxCount().collectAsState(initial = 0)
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    // the keys stay off until the dots have shaken and cleared, so a quick next digit is never swallowed
    suspend fun wrong(message: String) {
        error = message
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        repeat(3) { shake.animateTo(14f, tween(45)); shake.animateTo(-14f, tween(45)) }
        shake.animateTo(0f, tween(45))
        digits = ""
    }

    fun submit(pin: String) {
        busy = true
        scope.launch {
            when (val r = Api(Settings(activity), SessionStore(activity)).verifyPin(pin)) {
                is ApiResult.Ok -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    AppLock.unlockWithPin()
                    onUnlocked()
                }
                is ApiResult.Fail -> {
                    // five wrong PINs end the session on the server (F-01.2): sign in again with the code; local data stays
                    if (r.code == "SESI_BERAKHIR" || r.code == "TANPA_SESI") { SessionStore(activity).set(null); onSessionEnded() }
                    else { offline = false; wrong(r.message) }
                }
                ApiResult.Offline -> { offline = true; wrong("Tidak ada koneksi. PIN tidak bisa diperiksa.") }
            }
            busy = false
        }
    }

    fun press(d: Char) {
        if (busy || digits.length >= 6) return
        digits += d
        error = null
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (digits.length == 6) submit(digits)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        GsLogo(44.dp, bg = Color.White, fg = Blue)
        Spacer(Modifier.weight(0.7f))
        Text("Masukkan PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            error ?: "PIN 6 angka",
            style = MaterialTheme.typography.bodyMedium, color = if (error != null) ErrorOnBlue else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 40.dp).semantics { if (error != null) contentDescription = "Galat: $error" },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.offset(x = shake.value.dp).semantics { contentDescription = "${digits.length} dari 6 angka dimasukkan" },
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(6) { i ->
                val on = i < digits.length
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent).border(1.5.dp, Color.White.copy(alpha = if (on) 1f else 0.55f), CircleShape))
            }
        }
        Spacer(Modifier.weight(0.5f))
        // offline: the PIN cannot be checked, so the phone's own lock is the one allowed stand-in (K-16)
        if (offline && AppLock.canCredential(activity)) {
            Text(
                "Buka dengan kunci layar HP", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Blue,
                modifier = Modifier.padding(bottom = 16.dp).clip(CircleShape).background(Color.White).clickable { AppLock.promptCredential(activity, onUnlocked) }.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { d -> PinKey("Angka $d", enabled = !busy, onClick = { press(d) }) { Text("$d", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = Color.White) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (biometric) PinKey("Pakai sidik jari", enabled = !busy, plain = true, onClick = onFingerprint) { Icon(Ic.fingerprint, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                else Spacer(Modifier.size(72.dp))
                PinKey("Angka 0", enabled = !busy, onClick = { press('0') }) { Text("0", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = Color.White) }
                PinKey("Hapus satu angka", enabled = !busy && digits.isNotEmpty(), plain = true, onClick = { digits = digits.dropLast(1) }) { Icon(Ic.delete, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Lupa PIN? Masuk ulang", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 16.dp).clip(CircleShape).clickable { confirmOut = true }.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    if (confirmOut) AlertDialog(
        onDismissRequest = { confirmOut = false },
        containerColor = Token.permukaan,
        title = { Text("Masuk ulang?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Anda keluar dari HP ini lalu masuk lagi dengan email, kata sandi, dan kode autentikator. Setelah itu PIN baru bisa diatur di Pengaturan. Data di HP ini dihapus; data di server aman.")
                if (waiting > 0) Text("$waiting perubahan belum terkirim akan hilang.", color = Token.galat, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(600)))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    Api(Settings(activity), SessionStore(activity)).logout()
                    Db.wipe(activity)
                    onSessionEnded()
                }
            }) { Text("Keluar", color = Token.galat) }
        },
        dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Batal") } },
    )
}

/** A round key. [plain] draws it without the frosted disc (fingerprint, delete) so the digits stay the main thing. */
@Composable
private fun PinKey(description: String, enabled: Boolean, onClick: () -> Unit, plain: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(if (plain) Color.Transparent else Color.White.copy(alpha = 0.14f))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}
