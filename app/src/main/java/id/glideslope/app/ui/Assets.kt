// S-11 Aset (F-07): total funds, this month's change, the liquidity ladder, accounts with their balance source,
// and every investment with value and gain. Figures come from the server summary cached on the phone.
package id.glideslope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.glideslope.app.data.Db
import id.glideslope.core.formatPct
import id.glideslope.core.ladderRows
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// one shade per ladder position; the labelled list carries the meaning, not colour alone
private val SHADES = listOf(
    Token.aksen, Token.aksenTinggi, Token.aksenSedang, Color(0xFF9AA5B5), Color(0xFFC9D0DA),
    Color(0xFF3F4F66), Color(0xFF5F6B7C), Color(0xFF7F8CA0), Color(0xFFB3BCC9), Color(0xFFE3E8EF),
)

private val TYPE_LABEL = mapOf(
    "money_market_fund" to "Reksa dana pasar uang", "fixed_income_fund" to "Reksa dana pendapatan tetap", "equity_fund" to "Reksa dana saham",
    "deposit" to "Deposito", "bond" to "Obligasi & SBN", "gold" to "Emas", "stock" to "Saham", "crypto" to "Kripto", "other" to "Lainnya",
)

@Composable
fun AssetsScreen(pad: PaddingValues) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).sync() }
    val summaryJson by dao.metaFlow("summary").collectAsState(initial = null)
    val s = rememberSummary(summaryJson)
    val hidden = LocalPrivacy.current.hidden
    val accounts = s?.arr("accounts")?.map { it.jsonObject }?.filter { it.text("is_system") != "true" && it.text("archived") != "true" }.orEmpty()
    val assets = s?.arr("assets")?.map { it.jsonObject }.orEmpty()
    val cash = accounts.sumOf { it.long("balance") ?: 0 }
    val byType = assets.groupBy { it.text("type") ?: "other" }.mapValues { (_, v) -> v.sumOf { it.long("value") ?: 0 } }
    val (total, ladder) = ladderRows(byType + ("cash" to cash))
    val market = assets.sumOf { it.long("market") ?: 0 }

    Column(
        Modifier.fillMaxSize().padding(pad).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle("Aset")
        if (s == null) { SkeletonCard(3); SkeletonCard(5); return@Column }
        if (s["accounts"] == null) {
            InfoBanner("Server Glideslope belum mengirim data aset. Perbarui server (deploy terbaru), lalu tarik ulang.", warning = true, action = "Tarik ulang", onAction = { id.glideslope.app.sync.PullWorker.now(ctx) })
            SkeletonCard(3)
            return@Column
        }
        GsCard {
            Text("Total dana", style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup)
            BigAmount(total)
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Token.garis)
            Text("Perubahan bulan ini", style = MaterialTheme.typography.labelMedium, color = Token.teksRedup)
            ChangeRow("Dipakai", -(s.long("month_spend") ?: 0), Ic.arrowUpRight)
            ChangeRow("Perubahan pasar", market, Ic.arrows)
        }
        GsCard {
            Text("Tangga likuiditas", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
            Text("Dari yang paling cepat cair", style = MaterialTheme.typography.bodyMedium, color = Token.teksRedup)
            if (total > 0) Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp).height(24.dp).clip(CircleShape)
                    .semantics { contentDescription = "Komposisi dana: " + ladder.joinToString(", ") { "${it.step.label} ${it.pct?.let(::formatPct) ?: "—"}" } },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ladder.forEachIndexed { i, r -> if (r.value > 0) Box(Modifier.weight(r.value.toFloat()).fillMaxHeight().background(SHADES[i % SHADES.size])) }
            }
            ladder.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = Token.garis)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(SHADES[i % SHADES.size]))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(r.step.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)))
                        Text(r.step.speed, style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupiah(r.value), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)))
                        Text(if (hidden) "••" else r.pct?.let(::formatPct) ?: "—", style = MaterialTheme.typography.bodySmall, color = Token.teksRedup)
                    }
                }
            }
        }
        SectionHeader("Rekening & wallet")
        GsCard(padding = PaddingValues(vertical = 4.dp)) {
            accounts.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = Token.garis, modifier = Modifier.padding(start = 76.dp))
                AccountLine(a)
            }
        }
        if (assets.isNotEmpty()) {
            SectionHeader("Investasi")
            GsCard(padding = PaddingValues(vertical = 4.dp)) {
                assets.sortedByDescending { it.long("value") ?: 0 }.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = Token.garis, modifier = Modifier.padding(start = 16.dp))
                    AssetLine(a)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
private fun ChangeRow(label: String, amount: Long, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Token.teksRedup, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Token.teksRedup, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text((if (amount < 0) "−" else if (amount > 0) "+" else "") + rupiah(kotlin.math.abs(amount)), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)))
    }
}

@Composable
private fun AccountLine(a: JsonObject) {
    val label = a.obj("label")
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(when (a.text("kind")) { "ewallet" -> Ic.phone; "cash" -> Ic.cash; else -> Ic.bank }, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(a.text("name") ?: "", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            label?.text("text")?.let { t ->
                val warn = label.text("kind") == "selisih"
                Text((if (warn) "! " else "") + t, style = MaterialTheme.typography.bodySmall, color = if (warn) Token.peringatan else Token.teksRedup, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(rupiah(a.long("balance") ?: 0), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)))
    }
}

@Composable
private fun AssetLine(a: JsonObject) {
    val value = a.long("value") ?: 0
    val cost = a.long("cost_basis")
    val gain = cost?.let { value - it }
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.text("name") ?: "", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(TYPE_LABEL[a.text("type")], a.text("platform"), if (a.text("stale") == "true") "harga perlu diperbarui" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = if (a.text("stale") == "true") Token.peringatan else Token.teksRedup,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rupiah(value), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)))
            gain?.takeIf { cost > 0 }?.let { g ->
                val pct = g * 1000 / cost / 10.0
                Text(
                    (if (g >= 0) "+" else "−") + rupiah(kotlin.math.abs(g)) + if (LocalPrivacy.current.hidden) "" else " (${formatPct(kotlin.math.abs(pct))})",
                    style = MaterialTheme.typography.bodySmall, color = if (g >= 0) Token.masuk else Token.galat,
                )
            }
        }
    }
}

