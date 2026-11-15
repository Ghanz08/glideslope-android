// F-14.1 pull: pages of changes after the saved cursor, each page and its cursor stored in one local transaction,
// so a killed process resumes exactly where it stopped. A new server epoch (restored backup) rebuilds the cache.
package id.glideslope.app.sync

import androidx.room.withTransaction
import id.glideslope.app.data.AccountEntity
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.CategoryEntity
import id.glideslope.app.data.Db
import id.glideslope.app.data.MetaEntity
import id.glideslope.app.data.SyncRow
import id.glideslope.app.data.TxEntity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

sealed interface PullOutcome {
    data object Done : PullOutcome
    data object Offline : PullOutcome
    data class Failed(val code: String, val message: String) : PullOutcome
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
private fun JsonObject.bool(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean ?: false

class Syncer(private val db: Db, private val api: Api) {
    private val dao = db.sync()

    suspend fun pull(): PullOutcome {
        var xid = dao.meta("cursor_xid") ?: "0"
        var seq = dao.meta("cursor_seq")?.toLong() ?: 0L
        while (true) {
            val page = when (val r = api.pull(xid, seq)) {
                is ApiResult.Ok -> r.value
                is ApiResult.Fail -> return PullOutcome.Failed(r.code, r.message)
                ApiResult.Offline -> return PullOutcome.Offline
            }
            val epoch = page.str("epoch")
            val known = dao.meta("epoch")
            if (page.bool("reset") || (known != null && known != epoch)) {
                db.withTransaction {
                    dao.clearRows(); dao.clearTx(); dao.clearAccounts(); dao.clearCategories(); dao.clearMeta()
                    epoch?.let { dao.meta(MetaEntity("epoch", it)) }
                }
                xid = "0"; seq = 0
                continue
            }
            val next = page["next"]!!.jsonObject
            db.withTransaction {
                for (c in page["changes"]!!.jsonArray) apply(c.jsonObject)
                xid = next.str("xid")!!
                seq = next["seq"]!!.jsonPrimitive.long
                dao.meta(MetaEntity("cursor_xid", xid))
                dao.meta(MetaEntity("cursor_seq", seq.toString()))
                epoch?.let { dao.meta(MetaEntity("epoch", it)) }
            }
            if (!page.bool("has_more")) break
        }
        // F-14.4 referee and the figures Beranda shows until the Kotlin port (N3) computes them on the phone
        when (val s = api.summary()) {
            is ApiResult.Ok -> dao.meta(MetaEntity("summary", s.value.toString()))
            is ApiResult.Fail -> return PullOutcome.Failed(s.code, s.message)
            ApiResult.Offline -> return PullOutcome.Offline
        }
        // F-14.9: the Inbox the server computed (folded merchants, suggestions, order), so the phone and the web show the same list
        when (val i = api.get("/api/v1/layar/inbox")) {
            is ApiResult.Ok -> dao.meta(MetaEntity("inbox", i.value.toString()))
            is ApiResult.Fail -> return PullOutcome.Failed(i.code, i.message)
            ApiResult.Offline -> return PullOutcome.Offline
        }
        dao.meta(MetaEntity("synced_at", System.currentTimeMillis().toString()))
        return PullOutcome.Done
    }

    private suspend fun apply(c: JsonObject) {
        val tbl = c.str("tbl")!!
        val key = c.str("key")!!
        val row = c["row"]?.takeIf { it !is JsonNull }?.jsonObject
        if (c.str("op") == "delete" || row == null) {
            dao.deleteRow(tbl, key)
            when (tbl) {
                "transactions" -> dao.deleteTx(key)
                "accounts" -> dao.deleteAccount(key)
                "categories" -> dao.deleteCategory(key)
            }
            return
        }
        dao.row(SyncRow(tbl, key, row.toString()))
        when (tbl) {
            "transactions" -> dao.tx(
                TxEntity(
                    id = key,
                    accountId = row.str("account_id")!!,
                    direction = row.str("direction")!!,
                    amount = row["amount"]!!.jsonPrimitive.long,
                    occurredAt = row.str("occurred_at")!!,
                    kind = row.str("kind")!!,
                    status = row.str("status")!!,
                    categoryId = row.str("category_id"),
                    merchantRaw = row.str("merchant_raw"),
                    note = row.str("note"),
                    isOneOff = row.bool("is_one_off"),
                    deletedAt = row.str("deleted_at"),
                    updatedAt = row.str("updated_at"),
                ),
            )
            "accounts" -> dao.account(AccountEntity(key, row.str("name")!!, row.str("kind")!!, row.bool("is_system"), row.str("archived_at")))
            "categories" -> dao.category(CategoryEntity(key, row.str("name")!!, row.str("kind")!!))
        }
    }
}
