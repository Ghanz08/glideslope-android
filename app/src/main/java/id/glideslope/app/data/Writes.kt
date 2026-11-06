// F-14.2 writes: the change lands in Room at once (marked pending) and an outbox entry carries it to the server.
package id.glideslope.app.data

import android.content.Context
import androidx.room.withTransaction
import id.glideslope.app.sync.PushWorker
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class Writes(private val ctx: Context) {
    private val db = Db.get(ctx)
    private val dao = db.sync()

    private suspend fun enqueue(type: String, payload: JsonObject, localIds: List<String> = emptyList(), local: suspend () -> Unit = {}) {
        db.withTransaction {
            local()
            dao.outbox(OutboxEntity(opId = UUID.randomUUID().toString(), type = type, payload = payload.toString(), localIds = localIds.joinToString(",")))
        }
        PushWorker.now(ctx)
    }

    /** API-07 expense or income; the phone picks the id so the local row and the server row are the same one. */
    suspend fun createTx(
        kind: String, amount: Long, accountId: String, categoryId: String, merchant: String?, at: Instant,
        note: String?, oneOff: Boolean, fundedBy: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("id", id); put("client_id", id); put("kind", kind); put("amount", amount); put("account_id", accountId)
            put("category_id", categoryId); put("merchant_raw", merchant); put("occurred_at", at.toString()); put("note", note)
            put("is_one_off", oneOff); put("remember", true); put("funded_by", fundedBy)
        }
        // the local row is dropped once accepted; the pull then brings the server's row (same id once P-9 is deployed)
        enqueue("tx.create", payload, listOf(id)) {
            dao.tx(
                TxEntity(
                    id = id, accountId = accountId, direction = if (kind == "income") "in" else "out", amount = amount,
                    occurredAt = at.toString(), kind = kind, status = "confirmed", categoryId = categoryId, merchantRaw = merchant,
                    note = note, isOneOff = oneOff, deletedAt = null, pending = true,
                ),
            )
        }
        return id
    }

    /** API-08: the server makes both sides (and the fee); the phone shows temporary rows until the pull brings them. */
    suspend fun createTransfer(from: String, to: String, amount: Long, fee: Long, at: Instant) {
        val out = UUID.randomUUID().toString()
        val inn = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("client_id", UUID.randomUUID().toString()); put("from_account_id", from); put("to_account_id", to)
            put("amount", amount); put("fee", fee); put("occurred_at", at.toString())
        }
        enqueue("tx.transfer", payload, listOf(out, inn)) {
            fun side(id: String, acc: String, dir: String) = TxEntity(
                id = id, accountId = acc, direction = dir, amount = amount, occurredAt = at.toString(), kind = "transfer",
                status = "confirmed", categoryId = null, merchantRaw = "Transfer", note = null, isOneOff = false, deletedAt = null, pending = true,
            )
            dao.tx(side(out, from, "out")); dao.tx(side(inn, to, "in"))
        }
    }

    /** F-05.2 confirm from the Inbox; carries the version the phone saw so a laptop edit becomes a conflict. */
    suspend fun confirmReview(tx: TxEntity, categoryId: String?, accountId: String?) {
        val payload = buildJsonObject {
            put("transaction_id", tx.id); put("category_id", categoryId); put("account_id", accountId); put("remember", true)
            put("expected_updated_at", tx.updatedAt)
        }
        enqueue("review.confirm", payload) {
            dao.tx(tx.copy(status = "confirmed", categoryId = categoryId ?: tx.categoryId, accountId = accountId ?: tx.accountId, pending = true))
        }
    }

    /** ignore | mark | undo (5.3 status moves). */
    suspend fun moveReview(tx: TxEntity, move: String) {
        val to = when (move) { "ignore" -> "ignored"; else -> "needs_review" }
        enqueue("review.$move", buildJsonObject { put("id", tx.id) }) { dao.tx(tx.copy(status = to, pending = true)) }
    }

    suspend fun convertToTransfer(tx: TxEntity, toAccountId: String) =
        enqueue("tx.convert", buildJsonObject { put("transaction_id", tx.id); put("to_account_id", toAccountId) }) {
            dao.tx(tx.copy(kind = "transfer", status = "confirmed", categoryId = null, pending = true))
        }

    suspend fun deleteTx(tx: TxEntity) =
        enqueue("tx.delete", buildJsonObject { put("id", tx.id) }) { dao.tx(tx.copy(deletedAt = Instant.now().toString(), pending = true)) }

    suspend fun restoreTx(tx: TxEntity) =
        enqueue("tx.restore", buildJsonObject { put("id", tx.id) }) { dao.tx(tx.copy(deletedAt = null, pending = true)) }

    /** F-14.3: an edit carries the phone's copy of the row (`base`) so the server can merge or report a conflict. */
    suspend fun editTx(tx: TxEntity, patch: JsonObject, updated: TxEntity) {
        val base = buildJsonObject {
            put("amount", tx.amount); put("account_id", tx.accountId); put("category_id", tx.categoryId); put("merchant_raw", tx.merchantRaw)
            put("note", tx.note); put("occurred_at", tx.occurredAt); put("is_one_off", tx.isOneOff)
        }
        val payload = buildJsonObject {
            put("id", tx.id); put("base_version", tx.updatedAt); put("base", base); put("patch", patch); put("remember", true)
        }
        enqueue("tx.edit", payload) { dao.tx(updated.copy(pending = true)) }
    }
}
