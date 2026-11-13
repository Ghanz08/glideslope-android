// The "+" sheet (S-06), Pengaturan hub and the placeholder for screens still being built natively.
package id.glideslope.app.ui

import androidx.compose.foundation.clickable
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

/** "Lainnya" (S-20): the screens that are not tabs, the theme, the notification forwarder and sign-out. */
@Composable
fun SettingsHub(pad: PaddingValues, onBack: () -> Unit, onOpen: (String) -> Unit, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chevronLeft, null, tint = Token.teks, modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp).semantics { contentDescription = "Kembali" })
            Text("Lainnya", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GsCard(padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                listOf(
                    "analitik" to ("Analitik" to Ic.list), "anggaran" to ("Anggaran" to Ic.wallet), "langganan" to ("Langganan" to Ic.arrows),
                    "piutang" to ("Piutang" to Ic.users), "rencana" to ("Rencana pengeluaran" to Ic.info), "label" to ("Label" to Ic.list),
                    "sumber" to ("Sumber email" to Ic.inbox), "akun" to ("Rekening & wallet" to Ic.bank), "notifikasi" to ("Pemantau notifikasi HP" to Ic.camera), "pengaturan" to ("Pengaturan" to Ic.user),
                ).forEachIndexed { i, (key, v) ->
                    if (i > 0) HorizontalDivider(color = Token.garis)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(key) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconTile(v.second, bg = Token.aksenLembut, fg = Token.aksenTeks, size = 40.dp)
                        Text(v.first, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Ic.chevronRight, null, tint = Token.teksRedup, modifier = Modifier.size(20.dp))
                    }
                }
            }
            GsCard {
                Text("Tampilan", style = MaterialTheme.typography.titleMedium)
                Text("Terang, Gelap, atau ikuti sistem HP", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                Spacer(Modifier.height(12.dp))
                val modes = listOf("terang", "gelap", "sistem")
                Segmented(listOf("Terang", "Gelap", "Sistem"), modes.indexOf(ThemeMode.mode).coerceAtLeast(0)) { ThemeMode.set(ctx, modes[it]) }
            }
            Text(
                "Keluar dan hapus data di HP ini",
                color = Token.galat,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        Api(Settings(ctx), SessionStore(ctx)).logout()
                        Db.wipe(ctx)
                        onSignedOut()
                    }
                }.padding(vertical = 16.dp),
            )
        }
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
