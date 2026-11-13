// Building blocks matching the PWA's components (PRD 4.2–4.4): card, big amount, status pill, segmented control,
// banner, privacy mode and the bottom navigation with the centre "+" button.
package id.glideslope.app.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.core.formatRupiah

// ---------------------------------------------------------------- privacy mode (F-06: hide every amount)

class Privacy(private val ctx: Context) {
    var hidden by mutableStateOf(ctx.getSharedPreferences("tampilan", Context.MODE_PRIVATE).getBoolean("sembunyi", false))
        private set

    fun toggle() {
        hidden = !hidden
        ctx.getSharedPreferences("tampilan", Context.MODE_PRIVATE).edit().putBoolean("sembunyi", hidden).apply()
    }
}

val LocalPrivacy = staticCompositionLocalOf<Privacy> { error("Privacy not provided") }

@Composable
fun ProvidePrivacy(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    CompositionLocalProvider(LocalPrivacy provides remember { Privacy(ctx.applicationContext) }, content = content)
}

/** "Rp 1.234" or "Rp •••" in privacy mode. */
@Composable
fun rupiah(amount: Long): String = if (LocalPrivacy.current.hidden) "Rp •••" else formatRupiah(amount)

// ---------------------------------------------------------------- loading placeholders

/** A soft pulsing block that stands where data will appear (never a blank screen). */
@Composable
fun Skeleton(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween<Float>(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Token.isian.copy(alpha = a)))
}

@Composable
fun SkeletonCard(lines: Int = 3) {
    GsCard(Modifier.fillMaxWidth().semantics { contentDescription = "Memuat data" }) {
        Skeleton(Modifier.width(120.dp).height(16.dp))
        Spacer(Modifier.height(12.dp))
        Skeleton(Modifier.width(220.dp).height(40.dp))
        repeat(lines) {
            Spacer(Modifier.height(14.dp))
            Skeleton(Modifier.fillMaxWidth().height(18.dp))
        }
    }
}

/** The cached server summary, parsed off the main thread. */
@Composable
fun rememberSummary(json: String?): kotlinx.serialization.json.JsonObject? {
    val state = androidx.compose.runtime.produceState<kotlinx.serialization.json.JsonObject?>(null, json) {
        value = json?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject }.getOrNull() } }
    }
    return state.value
}

// ---------------------------------------------------------------- surfaces

@Composable
fun GsCard(
    modifier: Modifier = Modifier,
    color: Color = Token.permukaan,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .shadow(2.dp, shape, ambientColor = Token.teks.copy(alpha = 0.06f), spotColor = Token.teks.copy(alpha = 0.08f))
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PageTitle(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup) }
        }
        action?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, link: String? = null, onLink: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp), modifier = Modifier.weight(1f).semantics { heading() })
        if (link != null && onLink != null) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onLink).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(link, color = Token.aksenTeks, style = MaterialTheme.typography.labelLarge)
                Icon(Ic.chevronRight, null, tint = Token.aksenTeks, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The hero amount: a small grey "Rp" before a large tabular number (PRD 4.2: 38/46 bold, −0.03em). */
@Composable
fun BigAmount(amount: Long, modifier: Modifier = Modifier) {
    val hidden = LocalPrivacy.current.hidden
    val digits = formatRupiah(amount).removePrefix("Rp ")
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = if (hidden) "Jumlah disembunyikan" else "Rp $digits" }, verticalAlignment = Alignment.Bottom) {
        Text("Rp", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight(600)), color = Token.teksPelan, modifier = Modifier.padding(bottom = 5.dp, end = 8.dp))
        Text(if (hidden) "•••••••" else digits, style = MaterialTheme.typography.displaySmall, color = Token.teks)
    }
}

@Composable
fun Pill(text: String, icon: ImageVector?, bg: Color, fg: Color) {
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Token.isian).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) Token.permukaan else Color.Transparent)
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Token.teks else Token.teksRedup)
            }
        }
    }
}

@Composable
fun InfoBanner(text: String, warning: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(if (warning) Token.peringatanLatar else Token.aksenLembut).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (warning) Ic.triangleAlert else Ic.info, null, tint = if (warning) Token.peringatan else Token.aksenTeks, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Token.teks)
            if (action != null && onAction != null) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Token.aksenTeks, modifier = Modifier.padding(top = 6.dp).clickable(onClick = onAction))
            }
        }
        onClose?.let { Icon(Ic.x, "Tutup", tint = Token.teksRedup, modifier = Modifier.size(20.dp).clickable(onClick = it)) }
    }
}

@Composable
fun IconTile(icon: ImageVector, bg: Color = Token.isian, fg: Color = Token.teks, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = Token.teks)
    }
}

// ---------------------------------------------------------------- bottom navigation (PRD 4.4: 5 slots, 80 dp, "+" 52 dp)

enum class Tab(val label: String, val icon: ImageVector) {
    BERANDA("Beranda", Ic.home), INBOX("Inbox", Ic.inbox), TRANSAKSI("Transaksi", Ic.list), ASET("Aset", Ic.wallet)
}

/** The bar's glass: a 12 dp blur of what scrolls underneath, tinted with --color-kaca (rebuilt only when the theme changes). */
@Composable
private fun glassStyle(): dev.chrisbanes.haze.blur.HazeBlurStyle {
    val dark = Token.dark
    return remember(dark) {
        dev.chrisbanes.haze.blur.HazeBlurStyle {
            blurRadius(12.dp); noiseFactor(0f); backgroundColor(Token.latar)
            colorEffects(listOf(dev.chrisbanes.haze.blur.HazeColorEffect.tint(Token.kaca)))
        }
    }
}

/** Room the floating bar takes at the bottom (22 dp "+" overhang + 64 dp bar + 12 dp gap + system nav inset), so the last row can scroll clear of it. */
@Composable
fun barClearance(): Dp = 106.dp + androidx.compose.foundation.layout.WindowInsets.Companion.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * F-16.4 (nav.tsx BottomNav): a floating pill 16 dp from the sides, 12 dp above the bottom, 64 dp high, max 480 dp wide; five equal cells
 * with the 60 dp "+" in the middle, raised 22 dp above the bar's top edge. Android has no backdrop blur here, so the glass takes the PWA's
 * solid fallback (surface fill, hairline border).
 */
@Composable
fun BottomBar(current: Tab, inboxCount: Int, haze: dev.chrisbanes.haze.HazeState, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(50)
        Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 22.dp)) {
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .shadow(12.dp, shape, ambientColor = Token.teks.copy(alpha = 0.14f), spotColor = Token.teks.copy(alpha = 0.14f))
                    .clip(shape)
                    // the glass: what scrolls under the bar is blurred (12 dp, as --blur-kaca) and tinted with --color-kaca
                    .hazeBlur(dev.chrisbanes.haze.HazeInput.Sources(haze), glassStyle())
                    .border(1.dp, Token.kacaGaris, shape).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavSlot(Tab.BERANDA, current, 0, onTab)
                NavSlot(Tab.INBOX, current, inboxCount, onTab)
                Spacer(Modifier.weight(1f)) // the "+" floats over this cell
                NavSlot(Tab.TRANSAKSI, current, 0, onTab)
                NavSlot(Tab.ASET, current, 0, onTab)
            }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-22).dp).size(60.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Token.aksen.copy(alpha = 0.35f), spotColor = Token.aksen.copy(alpha = 0.35f))
                    .clip(CircleShape).background(Token.aksen)
                    .clickable(onClickLabel = "Catat transaksi", role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Catat transaksi" },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.plusBold, null, tint = Token.teksDiAksen, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun RowScope.NavSlot(tab: Tab, current: Tab, badge: Int, onTab: (Tab) -> Unit) {
    val on = tab == current
    Column(
        Modifier.weight(1f).heightIn(min = 44.dp).selectable(on, role = Role.Tab) { onTab(tab) }
            .semantics { contentDescription = tab.label + if (badge > 0) ", $badge item perlu dicek" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.width(48.dp).height(28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(if (on) Token.aksenLembut else Color.Transparent))
            Icon(tab.icon, null, tint = if (on) Token.aksenTeks else Token.teksRedup, modifier = Modifier.size(22.dp))
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).defaultMinSize(minWidth = 18.dp).height(18.dp)
                        .clip(CircleShape).background(Token.galat).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (badge > 99) "99+" else "$badge", color = Token.teksDiAksen, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
            }
        }
        Text(
            tab.label, maxLines = 1, overflow = TextOverflow.Clip, color = if (on) Token.aksenTeks else Token.teksRedup,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
        )
    }
}
