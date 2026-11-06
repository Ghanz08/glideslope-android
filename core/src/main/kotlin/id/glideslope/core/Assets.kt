// Ported from the PWA (src/lib/core/domain.ts, insights.ts): liquidity ladder and percentages that add up to 100.
package id.glideslope.core

import kotlin.math.floor

/** Splits `total` integer units by weight; leftovers go to the largest remainders, ties to the earlier item. */
fun largestRemainder(total: Long, weights: List<Long>): List<Long> {
    val sum = weights.sum()
    if (sum <= 0) return weights.map { 0L }
    val exact = weights.map { total.toDouble() * it / sum }
    val floors = exact.map { floor(it).toLong() }.toMutableList()
    var left = total - floors.sum()
    for (i in exact.indices.sortedWith(compareByDescending<Int> { exact[it] - floor(exact[it]) }.thenBy { it })) {
        if (left <= 0) break
        floors[i]++
        left--
    }
    return floors
}

/** Percentages with one decimal that always add up to exactly 100.0. */
fun percentages(values: List<Long>): List<Double> = largestRemainder(1000, values).map { it / 10.0 }

data class LadderStep(val key: String, val label: String, val speed: String, val volatile: Boolean)

val LADDER = listOf(
    LadderStep("cash", "Rekening & wallet", "Langsung", false),
    LadderStep("money_market_fund", "Reksa dana pasar uang", "1–3 hari kerja", false),
    LadderStep("fixed_income_fund", "Reksa dana pendapatan tetap", "2–7 hari kerja", true),
    LadderStep("deposit", "Deposito", "Jatuh tempo bertahap", false),
    LadderStep("bond", "Obligasi & SBN", "Jual ±2 hari kerja atau tunggu jatuh tempo", false),
    LadderStep("gold", "Emas", "Harga berfluktuasi", true),
    LadderStep("equity_fund", "Reksa dana saham & campuran", "Harga berfluktuasi", true),
    LadderStep("stock", "Saham", "Harga berfluktuasi · cair T+2", true),
    LadderStep("crypto", "Kripto", "Harga sangat berfluktuasi", true),
    LadderStep("other", "Lainnya", "Tergantung jenis", false),
)

data class LadderRow(val step: LadderStep, val value: Long, val pct: Double?)

/** Cash always shows; other steps only when they hold something. */
fun ladderRows(values: Map<String, Long>): Pair<Long, List<LadderRow>> {
    val steps = LADDER.filter { it.key == "cash" || (values[it.key] ?: 0) > 0 }
    val vals = steps.map { values[it.key] ?: 0 }
    val total = vals.sum()
    val pcts = if (total > 0) percentages(vals) else vals.map { null }
    return total to steps.mapIndexed { i, s -> LadderRow(s, vals[i], pcts[i]) }
}

fun formatPct(p: Double) = String.format(java.util.Locale.ROOT, "%.1f", p).replace('.', ',') + "%"
