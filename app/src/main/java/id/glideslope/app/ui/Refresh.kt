// F-14.9: pull-to-refresh for every screen, and the "Tersinkron … lalu" line that says how fresh the cache is.
package id.glideslope.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.glideslope.app.data.Db
import id.glideslope.app.sync.refreshAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The server-reading page on screen (RemoteScreen) registers its reload here, so a pull on any page also re-reads that page. */
object PageRefresh {
    var reload: (() -> Unit)? = null
}

/**
 * Pull down to run [onRefresh] (default: send the outbox, then pull). The indicator shows until it returns; a note it
 * returns (offline, failed) floats under the status bar for a few seconds, so a refresh is never silent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(modifier: Modifier = Modifier, onRefresh: (suspend () -> String?)? = null, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val state = rememberPullToRefreshState()
    LaunchedEffect(note) { if (note != null) { delay(3500); note = null } }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                note = runCatching { (onRefresh ?: { PageRefresh.reload?.invoke(); refreshAll(ctx) })() }.getOrElse { "Belum bisa menyegarkan. Coba lagi." }
                refreshing = false
            }
        },
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                containerColor = Token.permukaan, color = Token.aksen,
            )
        },
    ) {
        content()
        note?.let {
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
                Pill(it, Ic.info, Token.peringatanLatar, Token.peringatan)
            }
        }
    }
}

/** "Tersinkron 3 menit lalu", or the reason the last sync failed with "Coba lagi". */
@Composable
fun SyncLine(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).sync() }
    val syncedAt by dao.metaFlow("synced_at").collectAsState(initial = null)
    val error by dao.metaFlow("sync_error").collectAsState(initial = null)
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val failed = error?.takeIf { it.isNotBlank() }?.substringAfter('|')
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (failed != null) {
            Text(failed, style = MaterialTheme.typography.bodySmall, color = Token.galat, modifier = Modifier.weight(1f, fill = false))
            onRetry?.let { Text("Coba lagi", style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks, modifier = Modifier.clickableNoRipple(it)) }
        } else {
            val ms = syncedAt?.toLongOrNull()
            Text(
                if (ms == null) "Belum tersinkron" else "Tersinkron ${agoText(now - ms)}",
                style = MaterialTheme.typography.bodySmall, color = Token.teksPelan,
            )
        }
    }
}

private fun agoText(ms: Long): String {
    val min = (ms / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "baru saja"
        min < 60 -> "$min menit lalu"
        min < 24 * 60 -> "${min / 60} jam lalu"
        else -> "${min / (24 * 60)} hari lalu"
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(
    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
)
