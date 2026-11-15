// Who sees what: sign in (password, then TOTP), unlock, then the app's tabs (F-14).
package id.glideslope.app.ui

import androidx.compose.foundation.layout.Arrangement
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.glideslope.app.AppLock
import id.glideslope.app.Boot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import id.glideslope.app.MainActivity
import id.glideslope.app.Settings
import id.glideslope.app.data.Db
import id.glideslope.app.data.SessionStore
import id.glideslope.app.sync.PullWorker

@Composable
fun Root(activity: MainActivity) {
    val sessions = remember { SessionStore(activity) }
    var aal by remember { mutableStateOf(sessions.get()?.aal) }
    var locked by remember { mutableStateOf(AppLock.locked) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { locked = AppLock.locked }

    when {
        aal != "aal2" -> AuthScreen(activity, onSignedIn = {
            aal = "aal2"
            AppLock.unlockWithoutPrompt() // the password and the code were just entered
            locked = false
            PullWorker.now(activity)
            PullWorker.schedule(activity)
        })
        locked && AppLock.available(activity) -> LockScreen(activity, onUnlocked = { locked = false }, onSessionEnded = { aal = null })
        else -> Tabs(activity, onSignedOut = { aal = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tabs(activity: MainActivity, onSignedOut: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.BERANDA) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var entry by rememberSaveable { mutableStateOf<String?>(null) } // pengeluaran | pemasukan | transfer
    var txDetail by rememberSaveable { mutableStateOf<String?>(null) }
    // screens opened from "Lainnya" or the "+" sheet: analitik, anggaran, …, nota, nota/<id>, notifikasi
    var screen by rememberSaveable { mutableStateOf<String?>(null) }
    val reviewCount = rememberInboxCards().size // the same cards (and count) the web shows
    LaunchedEffect(Unit) { PullWorker.now(activity); PullWorker.schedule(activity) }
    BackHandler(enabled = settings) { settings = false }
    screen?.let { dest ->
        val back = {
            when {
                dest.startsWith("nota/") -> screen = "nota" // from the review back to the scanner
                dest.startsWith("aset/") || dest.startsWith("bagi") -> screen = null
                dest.startsWith("orang/") -> screen = "piutang"
                dest == "nota" -> screen = null
                else -> { screen = null; settings = true } // from a Lainnya screen back to the hub
            }
        }
        BackHandler { back() }
        ProvidePrivacy { Refreshable {
            when {
                dest == "analitik" -> AnalyticsScreen(onBack = back)
                dest == "anggaran" -> BudgetScreen(onBack = back)
                dest == "langganan" -> SubscriptionsScreen(onBack = back)
                dest == "piutang" -> LoansScreen(onBack = back, onOpen = { screen = "orang/$it" })
                dest.startsWith("orang/") -> ContactScreen(dest.removePrefix("orang/"), onBack = { screen = "piutang" })
                dest == "rencana" -> PlannedScreen(onBack = back)
                dest == "label" -> LabelsScreen(onBack = back)
                dest == "sumber" -> SourcesScreen(onBack = back)
                dest == "notifikasi" -> ForwarderPage(onBack = back)
                dest == "akun" -> AccountsScreen(onBack = back)
                dest == "pengaturan" -> SettingsScreen(onBack = back)
                dest == "impor" -> StatementScreen(onBack = back)
                dest == "log" -> ActivityLogScreen(onBack = back)
                dest == "bagi" -> SplitStartScreen(onBack = { screen = null }, onOpen = { screen = "bagi/$it" })
                dest.startsWith("bagi/") -> SplitScreen(dest.removePrefix("bagi/"), onBack = { screen = null; tab = Tab.TRANSAKSI })
                dest.startsWith("aset/") -> AssetDetailScreen(dest.removePrefix("aset/"), onBack = { screen = null })
                dest == "nota" -> ScanScreen(onBack = back, onOpen = { screen = "nota/$it" })
                dest.startsWith("nota/") -> ReceiptReviewScreen(dest.removePrefix("nota/"), onBack = back, onDone = { screen = null; tab = Tab.TRANSAKSI }, onSplit = { screen = "bagi/$it" })
            }
        } }
        return
    }
    txDetail?.let { id ->
        BackHandler { txDetail = null }
        ProvidePrivacy { Refreshable { TxDetailScreen(id, onBack = { txDetail = null }, onSplit = { txDetail = null; screen = "bagi/$it" }) } }
        return
    }
    entry?.let { kind ->
        BackHandler { entry = null }
        AddTxScreen(kind, onDone = { entry = null })
        return
    }
    ProvidePrivacy {
        // the content fills the screen and scrolls behind the floating bar (as the PWA); screens add barClearance() to their last row
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Token.latar)) {
            val pad = PaddingValues(0.dp)
            val haze = remember { dev.chrisbanes.haze.HazeState() }
            Refreshable(Modifier.hazeSource(haze)) {
            when {
                settings -> SettingsHub(pad, onBack = { settings = false }, onOpen = { screen = it }, onSignedOut = onSignedOut)
                tab == Tab.BERANDA -> HomeScreen(pad, onOpenInbox = { tab = Tab.INBOX }, onOpenAset = { tab = Tab.ASET }, onOpenProfile = { settings = true })
                tab == Tab.TRANSAKSI -> TransactionsScreen(pad, onOpen = { txDetail = it })
                tab == Tab.INBOX -> InboxScreen(PaddingValues(bottom = barClearance()))
                tab == Tab.ASET -> AssetsScreen(pad, onOpenAsset = { screen = "aset/$it" }, onAccounts = { screen = "akun" })
                else -> ComingNext(pad, tab.label)
            }
            }
            if (!settings) androidx.compose.foundation.layout.Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(tab, reviewCount, haze, onTab = { tab = it }, onAdd = { adding = true }) }
        }
        if (adding) AddSheet(onDismiss = { adding = false }, onPick = {
            when (it) {
                "pengeluaran", "pemasukan", "transfer" -> entry = it
                "nota" -> screen = "nota"
                "bagi" -> screen = "bagi"
            }
        })
    }
}
