// Pure capture rules (PRD F-12.5): which notifications are forwarded and what text is sent.
// No Android types here, so it runs in plain JVM unit tests.
package id.glideslope.core

import java.time.Instant

/** The fields read from one posted notification, already known to come from an allowed package. */
data class Raw(
    val pkg: String,
    val key: String,
    val postTime: Long,
    val title: String?,
    val bigText: String?,
    val lines: List<String>,
    val text: String?,
    val groupSummary: Boolean,
    val ongoing: Boolean,
)

/** One webhook body (F-12.1) plus the key that keeps a re-posted notification from being queued twice. */
data class Payload(val sourceApp: String, val text: String, val postedAt: String, val dedupeKey: String) {
    fun toJson() = """{"source_app":${quote(sourceApp)},"text":${quote(text)},"posted_at":${quote(postedAt)}}"""
}

const val MAX_TEXT = 2000

fun capture(raw: Raw, allowed: Set<String>): Payload? {
    if (raw.pkg !in allowed || raw.groupSummary || raw.ongoing) return null
    // the expanded text is the fullest; inbox-style lines next; the short text last
    val body = raw.bigText?.takeIf { it.isNotBlank() }
        ?: raw.lines.filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }
        ?: raw.text
    // the webhook has no title field, so the title goes in front of the text
    val text = listOfNotNull(raw.title, body).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString("\n").take(MAX_TEXT)
    if (text.isEmpty()) return null
    // the server only reads ISO-8601; an update with new text is a new item, a re-post of the same text is not
    return Payload(raw.pkg, text, Instant.ofEpochMilli(raw.postTime).toString(), "${raw.key}|${text.hashCode()}")
}

fun quote(s: String): String = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}

enum class Outcome { SENT, RETRY, DROP, BAD_SECRET }

/** Server answer → what the queue does (F-12.1 answers 202, 401, 413, 422, 429). null = no answer (network). */
fun outcome(code: Int?): Outcome = when {
    code == null -> Outcome.RETRY
    code in 200..299 -> Outcome.SENT
    code == 401 -> Outcome.BAD_SECRET
    code == 408 || code == 429 || code >= 500 -> Outcome.RETRY
    else -> Outcome.DROP
}

/** Finance apps whose package names were checked on Google Play Indonesia (research 2026-11-06). */
val FINANCE_APPS = listOf(
    "com.gojek.app" to "Gojek (GoPay)",
    "com.gojek.gopay" to "GoPay",
    "ovo.id" to "OVO",
    "id.dana" to "DANA",
    "com.shopee.id" to "Shopee (ShopeePay)",
    "com.shopeepay.id" to "ShopeePay",
    "id.bni.wondr" to "wondr by BNI",
    "src.com.bni" to "BNI Mobile (lama)",
    "id.bmri.livin" to "Livin' by Mandiri",
    "com.jago.digitalBanking" to "Jago",
    "id.co.bankbkemobile.digitalbank" to "SeaBank",
    "com.bca" to "BCA mobile",
    "com.bca.mybca.omni.android" to "myBCA",
    "id.co.bri.brimo" to "BRImo",
    "com.bcadigital.blu" to "blu by BCA",
    "id.flip" to "Flip",
    "com.btpn.dc" to "Jenius",
)

/** E-wallets worth suggesting on the Status screen when they are installed but their notifications are not forwarded yet (F-22.7). */
private val WALLET_PACKAGES = setOf("com.gojek.gopay", "com.gojek.app", "ovo.id", "id.dana", "com.shopee.id", "com.shopeepay.id")

/** Installed e-wallet apps the owner has not allowed, in list order; [isInstalled] answers for one package name. */
fun suggestedWallets(allowed: Set<String>, isInstalled: (String) -> Boolean): List<Pair<String, String>> =
    FINANCE_APPS.filter { (pkg, _) -> pkg in WALLET_PACKAGES && pkg !in allowed && isInstalled(pkg) }
