// The "+" sheet (S-06), Pengaturan hub and the placeholder for screens still being built natively.
package id.glideslope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.Db
import id.glideslope.app.data.SessionStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onPick: (String) -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Token.permukaan, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tambah", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Icon(Ic.x, "Tutup", tint = Token.teksRedup, modifier = Modifier.size(28.dp).clickable(onClick = onDismiss))
            }
            listOf(
                Triple("pengeluaran", Ic.arrowUpRight, "Pengeluaran" to "Belanja, makan, atau tagihan"),
                Triple("pemasukan", Ic.arrowDownLeft, "Pemasukan" to "Uang saku, honor, atau transfer masuk"),
                Triple("transfer", Ic.arrows, "Transfer antar akun" to "Pindah saldo, bukan pemasukan"),
                Triple("nota", Ic.camera, "Scan nota" to "Foto nota, dicocokkan dengan email"),
                Triple("bagi", Ic.users, "Bagi tagihan" to "Pecah nota untuk beberapa teman"),
            ).forEachIndexed { i, (key, icon, text) ->
                if (i > 0) HorizontalDivider(color = Token.garis)
                SheetRow(icon, text.first, text.second) { onPick(key); onDismiss() }
            }
            Text("", Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(icon, bg = Token.aksenLembut, fg = Token.aksenTeks)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
        }
        Icon(Ic.chevronRight, null, tint = Token.teksRedup, modifier = Modifier.size(20.dp))
    }
}

/** "Lainnya" (S-20): the screens that are not tabs, grouped by purpose, then sync, theme and sign-out (F-14.9). */
@Composable
fun SettingsHub(pad: PaddingValues, onBack: () -> Unit, onOpen: (String) -> Unit, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { Db.get(ctx).sync() }
    val waiting by dao.outboxCount().collectAsState(initial = 0)
    var confirmOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp).semantics { contentDescription = "Kembali" })
            Text("Lainnya", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // freshness of the phone's copy, with a button for what pull-to-refresh does
            GsCard(padding = PaddingValues(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Data di HP", style = MaterialTheme.typography.titleMedium)
                        SyncLine()
                        if (waiting > 0) Text("$waiting perubahan menunggu dikirim", style = MaterialTheme.typography.bodySmall, color = Token.teksPelan)
                    }
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(CircleShape).background(Token.aksenLembut)
                            .clickable(enabled = !syncing, role = androidx.compose.ui.semantics.Role.Button) {
                                scope.launch { syncing = true; note = id.glideslope.app.sync.refreshAll(ctx); syncing = false }
                            }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (syncing) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Token.aksenTeks)
                        else Text("Segarkan", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks)
                    }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Token.peringatan, modifier = Modifier.padding(top = 8.dp)) }
            }
            HubGroup("Keuangan", listOf(
                HubItem("analitik", "Analitik", Ic.trendingDown), HubItem("anggaran", "Anggaran", Ic.wallet), HubItem("langganan", "Langganan", Ic.arrows),
                HubItem("piutang", "Utang-piutang", Ic.users), HubItem("rencana", "Rencana pengeluaran", Ic.cash),
            ), onOpen)
            HubGroup("Data", listOf(
                HubItem("akun", "Rekening & wallet", Ic.bank), HubItem("impor", "Impor mutasi bank", Ic.upload),
                HubItem("sumber", "Sumber email", Ic.inbox), HubItem("label", "Label", Ic.list),
            ), onOpen)
            HubGroup("Alat", listOf(
                HubItem("log", "Log aktivitas", Ic.scrollText), HubItem("notifikasi", "Pemantau notifikasi HP", Ic.bell), HubItem("pengaturan", "Pengaturan", Ic.lock),
            ), onOpen)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTitle("Tampilan")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeTile("terang", "Terang", Modifier.weight(1f))
                    ThemeTile("gelap", "Gelap", Modifier.weight(1f))
                    ThemeTile("sistem", "Ikuti HP", Modifier.weight(1f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTitle("Akun")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.large).background(Token.galatLatar)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { confirmOut = true }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Ic.lock, null, tint = Token.galat, modifier = Modifier.size(20.dp))
                    Text("Keluar dari HP ini", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = Token.galat, modifier = Modifier.weight(1f))
                }
                Text("Data di HP ini dihapus. Data di server aman dan dimuat lagi saat Anda masuk.", style = MaterialTheme.typography.bodySmall, color = Token.teksPelan, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmOut) AlertDialog(
        onDismissRequest = { if (!signingOut) confirmOut = false },
        containerColor = Token.permukaan,
        title = { Text("Keluar dari HP ini?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Semua data yang tersimpan di HP ini akan dihapus. Data di server tidak ikut terhapus; masuk lagi untuk memuatnya kembali.")
                if (waiting > 0) Text("$waiting perubahan belum terkirim akan hilang.", color = Token.galat, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = !signingOut, onClick = {
                signingOut = true
                scope.launch {
                    Api(Settings(ctx), SessionStore(ctx)).logout()
                    Db.wipe(ctx)
                    onSignedOut()
                }
            }) { Text(if (signingOut) "Keluar…" else "Keluar", color = Token.galat) }
        },
        dismissButton = { androidx.compose.material3.TextButton(enabled = !signingOut, onClick = { confirmOut = false }) { Text("Batal") } },
    )
}

private class HubItem(val key: String, val label: String, val icon: ImageVector)

@Composable
private fun HubTitle(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = Token.teksRedup, modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })

@Composable
private fun HubGroup(title: String, items: List<HubItem>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HubTitle(title)
        GsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items.forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider(color = Token.garis, modifier = Modifier.padding(start = 56.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(it.key) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconTile(it.icon, bg = Token.aksenLembut, fg = Token.aksenTeks, size = 40.dp)
                    Text(it.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Ic.chevronRight, null, tint = Token.teksRedup, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** One theme as a miniature of the app (surface, bars, a "+" dot) in that theme's own colours; the chosen one carries a ring and a tick. */
@Composable
private fun ThemeTile(mode: String, label: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val on = ThemeMode.mode == mode
    val shape = RoundedCornerShape(18.dp)
    val ring by androidx.compose.animation.animateColorAsState(if (on) Token.aksen else Token.garis, label = "ring")
    Column(
        modifier.clip(shape).background(Token.permukaan).border(if (on) 2.dp else 1.dp, ring, shape)
            .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton) { ThemeMode.set(ctx, mode) }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp))) {
            when (mode) {
                "terang" -> MiniApp(LightPalette, Modifier.fillMaxSize())
                "gelap" -> MiniApp(DarkPalette, Modifier.fillMaxSize())
                else -> Row(Modifier.fillMaxSize()) { // half and half: the phone decides
                    Box(Modifier.weight(1f).fillMaxSize()) { MiniApp(LightPalette, Modifier.fillMaxSize()) }
                    Box(Modifier.weight(1f).fillMaxSize()) { MiniApp(DarkPalette, Modifier.fillMaxSize().offset(x = (-24).dp)) }
                }
            }
            if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Token.aksen), contentAlignment = Alignment.Center) {
                Icon(Ic.circleCheck, null, tint = Token.teksDiAksen, modifier = Modifier.size(14.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Token.aksenTeks else Token.teks)
    }
}

@Composable
private fun MiniApp(p: Palette, modifier: Modifier) {
    Box(modifier.background(p.latar)) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.5f).height(7.dp).clip(CircleShape).background(p.teks.copy(alpha = 0.75f)))
            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(p.permukaan))
            Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape).background(p.teksPelan.copy(alpha = 0.6f)))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 44.dp, height = 12.dp).clip(CircleShape).background(p.permukaan))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(10.dp).clip(CircleShape).background(p.aksen))
    }
}

/** The notification forwarder (Status, Log, Setelan) on its own page. */
@Composable
fun ForwarderPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp).semantics { contentDescription = "Kembali" })
            Text("Pemantau notifikasi", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.weight(1f)) { ForwarderScreen(PaddingValues(0.dp)) }
    }
}

@Composable
fun ComingNext(pad: PaddingValues, title: String) {
    Column(Modifier.fillMaxSize().padding(pad).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageTitle(title)
        GsCard { Text("Layar $title native sedang dibuat dan menyusul di versi berikutnya.", color = Token.teksRedup) }
    }
}
