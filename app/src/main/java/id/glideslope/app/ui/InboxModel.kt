// F-14.9: what the Inbox shows. The list is the one the server computed (folded new merchants, suggestions, order) and
// cached by every pull, so the phone and the web agree; local state only hides cards the phone already settled.
package id.glideslope.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import id.glideslope.app.data.Db
import id.glideslope.app.data.TxEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class InboxCard(
    val key: String,
    /** Every transaction behind the card (one, or the whole "merchant baru" group). */
    val ids: List<String>,
    val title: String,
    val subtitle: String?,
    val merchant: String?,
    val amount: Long,
    val direction: String,
    val kind: String,
    val occurredAt: String,
    val accountId: String?,
    val categoryId: String?,
    val reason: String?,
    val proposals: List<JsonObject>,
)

/** Ids of suggestions accepted or rejected that the next pull has not removed from the cached list yet. */
object InboxSession {
    var dismissed by mutableStateOf(setOf<String>())
}

private val PROPOSAL_LABEL = mapOf(
    "transfer" to "Transfer antar akun?",
    "repayment" to "Pelunasan piutang?",
    "debt_payment" to "Bayar utang?",
    "loan_pair" to "Pinjaman yang sudah dikembalikan?",
    "receipt_link" to "Email + nota cocok",
    "duplicate_suspect" to "Diduga duplikat",
)

private fun JsonObject.str(k: String) = text(k)

private fun serverCard(item: JsonObject, dismissed: Set<String>): InboxCard? {
    val tx = item.obj("tx") ?: return null
    val ids = item.arr("groupIds")?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.takeIf { it.isNotEmpty() } ?: listOfNotNull(tx.str("id"))
    val proposals = item.arr("proposals")?.mapNotNull { it as? JsonObject }.orEmpty().filter { it.text("id") !in dismissed }
    val reason = tx.str("review_reason")
    val dir = tx.str("direction") ?: "out"
    val name = tx.str("merchant_display_name") ?: tx.str("merchant_raw") ?: "Transaksi"
    val count = item["groupCount"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: 1
    val p = proposals.firstOrNull()
    val who = p?.obj("contact")?.text("name") ?: p?.obj("receivable")?.text("person_name")
    var subtitle: String? = reason?.let { REASON_TEXT[it] }
    if (p != null) {
        subtitle = when (p.text("type")) {
            "repayment" -> if (who != null) "Cicilan dari $who?" else PROPOSAL_LABEL["repayment"]
            "debt_payment" -> if (who != null) "Bayar utang ke $who?" else PROPOSAL_LABEL["debt_payment"]
            "loan_pair" -> if (who != null) "Pinjaman $who sudah kembali?" else PROPOSAL_LABEL["loan_pair"]
            else -> PROPOSAL_LABEL[p.text("type")]
        }
    } else if ((tx.arr("evidence_roles")?.size ?: 0) >= 2) subtitle = "Email + nota cocok"
    if (reason == "hanya_nota") subtitle = "Nota tanpa email"
    if (reason == "transfer_tidak_jelas" && dir == "in" && p == null) subtitle = "Belum terkait piutang"
    if (item["selfTransfer"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } == "true") subtitle = "Transfer ke akunmu sendiri?"
    if (count > 1) subtitle = "Merchant baru · $count transaksi"
    val title = if (dir == "in" && reason == "transfer_tidak_jelas" && tx.str("kind") != "transfer") "Transfer masuk · $name" else name
    return InboxCard(
        key = item.text("key") ?: ids.first(), ids = ids, title = title, subtitle = subtitle, merchant = name,
        amount = tx.long("amount") ?: 0, direction = dir, kind = tx.str("kind") ?: "expense", occurredAt = tx.str("occurred_at") ?: "",
        accountId = tx.str("account_id"), categoryId = tx.str("category_id"), reason = reason, proposals = proposals,
    )
}

private fun localCard(t: TxEntity, reason: String?) = InboxCard(
    key = t.id, ids = listOf(t.id), title = t.merchantRaw ?: "Transaksi", subtitle = reason?.let { REASON_TEXT[it] }, merchant = t.merchantRaw,
    amount = t.amount, direction = t.direction, kind = t.kind, occurredAt = t.occurredAt, accountId = t.accountId, categoryId = t.categoryId,
    reason = reason, proposals = emptyList(),
)

/**
 * The cards to show. With the server's list: its cards in its order, minus those whose transactions the phone has already
 * confirmed or ignored (unless a suggestion is still open) and those settled this session; plus any local review row the
 * list does not know yet. Without it (first run offline): the local review rows, newest first.
 */
fun buildCards(snapshot: String?, reviewIds: Set<String>, queue: List<TxEntity>, reasonOf: Map<String, String>, dismissed: Set<String>): List<InboxCard> {
    val items = snapshot?.let { runCatching { Json.parseToJsonElement(it).jsonObject.arr("items") }.getOrNull() }
        ?.mapNotNull { (it as? JsonObject)?.let { o -> serverCard(o, dismissed) } }
    val covered = HashSet<String>()
    val cards = ArrayList<InboxCard>()
    items?.forEach { c ->
        covered += c.ids
        val open = c.ids.filter { it in reviewIds }
        if (open.isNotEmpty() || c.proposals.isNotEmpty()) cards += if (open.isNotEmpty() && open.size < c.ids.size) c.copyWithCount(open) else c
    }
    queue.filter { it.id !in covered && it.id in reviewIds }.forEach { cards += localCard(it, reasonOf[it.id]) }
    return cards
}

private fun InboxCard.copyWithCount(open: List<String>) = InboxCard(
    key, open, title, if (open.size > 1) "Merchant baru · ${open.size} transaksi" else subtitle, merchant, amount, direction, kind,
    occurredAt, accountId, categoryId, reason, proposals,
)

private val REASON_TEXT = mapOf(
    "merchant_baru" to "Merchant baru: pilih kategori sekali, berikutnya otomatis",
    "kepercayaan_rendah" to "Belum yakin dengan hasil bacaan",
    "nominal_besar" to "Nominal besar, periksa dulu",
    "transfer_internal" to "Sepertinya pindah uang ke akunmu sendiri",
    "akun_tidak_dikenal" to "Akun belum dikenali",
    "duplikat_diduga" to "Mungkin tercatat dua kali",
)

/** The Inbox cards as the screens see them (the tab badge and the screen itself share this). */
@Composable
fun rememberInboxCards(): List<InboxCard> {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).sync() }
    val snapshot by dao.metaFlow("inbox").collectAsState(initial = null)
    val ids by dao.reviewIds().collectAsState(initial = emptyList())
    val queue by dao.reviewQueue().collectAsState(initial = emptyList())
    val queueIds = remember(queue) { queue.map { it.id } }
    val rows by remember(queueIds) { dao.rowsByKey("transactions", queueIds) }.collectAsState(initial = emptyList())
    val reasonOf = remember(rows) {
        rows.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }.associate { (it.text("id") ?: "") to (it.text("review_reason") ?: "") }
    }
    val dismissed = InboxSession.dismissed
    return remember(snapshot, ids, queue, reasonOf, dismissed) { buildCards(snapshot, ids.toSet(), queue, reasonOf, dismissed) }
}
