// Three screens (F-12.5): Status with the setup checklist, Log of what was captured and sent, Setelan.
package id.glideslope.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as Sys
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.glideslope.app.DITOLAK
import id.glideslope.app.Item
import id.glideslope.app.Listener
import id.glideslope.app.MENUNGGU
import id.glideslope.app.Queue
import id.glideslope.app.SendWorker
import id.glideslope.app.Settings
import id.glideslope.app.TERKIRIM
import id.glideslope.core.FINANCE_APPS
import id.glideslope.core.Payload
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val wib = DateTimeFormatter.ofPattern("d MMM HH.mm", Locale.forLanguageTag("id")).withZone(ZoneId.of("Asia/Jakarta"))
private fun waktu(ms: Long) = wib.format(Instant.ofEpochMilli(ms)) + " WIB"
private fun appName(pkg: String) = FINANCE_APPS.firstOrNull { it.first == pkg }?.second ?: pkg

/** The notification forwarder (F-12.5) as one tab of the app: Status, Log and Setelan on top tabs. */
@Composable
fun ForwarderScreen(pad: PaddingValues) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // system settings change outside the app, so every return to the app re-reads them
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    Column(Modifier.fillMaxSize().padding(pad)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Status", "Log", "Setelan").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        val inner = PaddingValues(0.dp)
        when (tab) {
            0 -> StatusScreen(inner, tick, onOpenSettings = { tab = 2 }, refresh = { tick++ })
            1 -> LogScreen(inner, tick, refresh = { tick++ })
            else -> SettingsScreen(inner, saved = { tick++; tab = 0 })
        }
    }
}

private fun listenerOn(ctx: Context) = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)
private fun batteryFree(ctx: Context) = (ctx.getSystemService(PowerManager::class.java)).isIgnoringBatteryOptimizations(ctx.packageName)

private fun openListenerSettings(ctx: Context) {
    val detail = Intent(Sys.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Sys.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, Listener::class.java).flattenToString())
    val intent = if (Build.VERSION.SDK_INT >= 30) detail else Intent(Sys.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    runCatching { ctx.startActivity(intent) }.onFailure { ctx.startActivity(Intent(Sys.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
}

private fun openAppInfo(ctx: Context) =
    ctx.startActivity(Intent(Sys.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))

private fun askBattery(ctx: Context) = runCatching {
    ctx.startActivity(Intent(Sys.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", ctx.packageName, null)))
}.onFailure { ctx.startActivity(Intent(Sys.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun Step(done: Boolean, title: String, help: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        // the state is spelled out, never colour alone
        Text(if (done) "✓" else "!", color = if (done) Token.masuk else Token.peringatan, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$title · ${if (done) "selesai" else "belum"}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (!done) {
                Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null) OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
            }
        }
    }
}

@Composable
private fun StatusScreen(pad: PaddingValues, tick: Int, onOpenSettings: () -> Unit, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember(tick) { Settings(ctx) }
    val q = remember { Queue.get(ctx) }
    val listener = remember(tick) { listenerOn(ctx) }
    val battery = remember(tick) { batteryFree(ctx) }
    val waiting = remember(tick) { q.count(MENUNGGU) }
    val rejected = remember(tick) { q.count(DITOLAK) }
    val ok = listener && s.ready && s.allowed.isNotEmpty()
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Penerus notifikasi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Card(
            colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else Token.peringatanLatar),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (ok) "✓ Berjalan" else "! Perlu tindakan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (s.lastSentAt > 0) "Terakhir terkirim ${waktu(s.lastSentAt)}" else "Belum ada yang terkirim")
                Text("Menunggu dikirim: $waiting · Ditolak server: $rejected")
                if (s.secretRejected) Text("Rahasia ditolak server. Perbarui di Setelan.", color = Token.galat, fontWeight = FontWeight.SemiBold)
            }
        }
        Section("Pemasangan") {
            Step(
                listener, "Akses notifikasi",
                "Izinkan Glideslope membaca notifikasi. Kalau tombolnya abu-abu atau muncul \"Setelan terbatas\": buka Info aplikasi, ketuk ⋮, pilih Izinkan setelan terbatas, lalu coba lagi.",
                "Buka setelan akses", { openListenerSettings(ctx) },
            )
            if (!listener) TextButton(onClick = { openAppInfo(ctx) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Buka Info aplikasi") }
            HorizontalDivider()
            Step(
                battery, "Baterai tidak dibatasi",
                "Supaya notifikasi tetap terbaca saat layar mati. Di Samsung, setel juga baterai aplikasi ke Tidak dibatasi dan masukkan ke Aplikasi yang tidak pernah tidur. Setelan ini bisa kembali semula setelah update sistem.",
                "Izinkan", { askBattery(ctx) },
            )
            HorizontalDivider()
            Step(s.ready, "Server dan rahasia", "Isi alamat Glideslope dan rahasia webhook.", "Buka Setelan", onOpenSettings)
            HorizontalDivider()
            Step(s.allowed.isNotEmpty(), "Aplikasi keuangan", "Pilih aplikasi yang notifikasinya diteruskan.", "Pilih aplikasi", onOpenSettings)
        }
        Button(
            onClick = {
                val now = System.currentTimeMillis()
                Queue.get(ctx).add(Payload(ctx.packageName, "Tes koneksi Glideslope", Instant.ofEpochMilli(now).toString(), "tes|$now"))
                SendWorker.enqueue(ctx)
                refresh()
            },
            enabled = s.ready,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Kirim tes") }
    }
}

@Composable
private fun LogScreen(pad: PaddingValues, tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember(tick) { Queue.get(ctx).recent() }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log 7 hari", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { Queue.get(ctx).purge(clearAll = true); refresh() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus log") }
            }
            Text("Hanya notifikasi dari aplikasi yang kamu pilih. Teks di bawah persis yang dikirim ke server.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (items.isEmpty()) item { Text("Belum ada notifikasi tertangkap.") }
        items(items, key = Item::id) { LogRow(it) }
    }
}

@Composable
private fun LogRow(it: Item) {
    val (label, color) = when (it.status) {
        TERKIRIM -> "Terkirim" to Token.masuk
        DITOLAK -> "Ditolak (${it.result})" to Token.galat
        else -> "Menunggu" to Token.peringatan
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(appName(it.sourceApp), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(label, color = color, fontWeight = FontWeight.SemiBold)
            }
            Text(waktu(Instant.parse(it.postedAt).toEpochMilli()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun SettingsScreen(pad: PaddingValues, saved: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { Settings(ctx) }
    var secret by rememberSaveable { mutableStateOf("") }
    var allowed by remember { mutableStateOf(s.allowed) }
    val hasSecret = remember { !s.secret.isNullOrEmpty() }
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Setelan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Section("Server") {
            OutlinedTextField(
                secret, { secret = it }, label = { Text("Rahasia webhook") },
                placeholder = { Text(if (hasSecret) "Tersimpan · isi untuk mengganti" else "") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
        }
        Section("Aplikasi yang diteruskan") {
            Text("Notifikasi aplikasi lain tidak dibaca sama sekali.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FINANCE_APPS.forEach { (pkg, name) ->
                val installed = remember(pkg) { runCatching { @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess }
                val on = pkg in allowed
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(on, role = Role.Checkbox) { allowed = if (it) allowed + pkg else allowed - pkg },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(name + if (installed) "" else " (tidak terpasang)", color = if (installed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(
            onClick = {
                if (secret.isNotBlank()) s.secret = secret.trim()
                s.allowed = allowed
                SendWorker.enqueue(ctx)
                saved()
            },
            enabled = (hasSecret || secret.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Simpan") }
    }
}
