// Who sees what: sign in (password, then TOTP), unlock, then the app's tabs (F-14).
package id.glideslope.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import id.glideslope.app.MainActivity
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
        locked && AppLock.available(activity) -> LockScreen { AppLock.prompt(activity) { locked = false } }
        else -> Tabs(activity, onSignedOut = { aal = null })
    }
}

@Composable
private fun LockScreen(unlock: () -> Unit) {
    LaunchedEffect(Unit) { unlock() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Glideslope terkunci", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Button(onClick = unlock, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 48.dp)) { Text("Buka kunci") }
    }
}

@Composable
private fun Tabs(activity: MainActivity, onSignedOut: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { PullWorker.now(activity); PullWorker.schedule(activity) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Beranda", "Transaksi", "Penerus").forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        when (tab) {
            0 -> HomeScreen(pad, onSignedOut)
            1 -> TransactionsScreen(pad)
            else -> ForwarderScreen(pad)
        }
    }
}
