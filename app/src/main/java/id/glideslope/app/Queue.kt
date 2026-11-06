// Local queue and log in one SQLite table: notifications wait here until the webhook accepts them.
package id.glideslope.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import id.glideslope.core.Payload

data class Item(val id: Long, val sourceApp: String, val text: String, val postedAt: String, val status: String, val result: String?, val createdAt: Long) {
    fun payload() = Payload(sourceApp, text, postedAt, "")
}

const val MENUNGGU = "menunggu"
const val TERKIRIM = "terkirim"
const val DITOLAK = "ditolak"
private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000 // the log keeps 7 days (F-12.5)

class Queue private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "queue.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """create table item (id integer primary key, dedupe text not null unique, source_app text not null,
               text text not null, posted_at text not null, status text not null, result text, created_at integer not null)""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

    /** false when the same notification was queued before. */
    fun add(p: Payload): Boolean = writableDatabase.insertWithOnConflict(
        "item", null,
        ContentValues().apply {
            put("dedupe", p.dedupeKey); put("source_app", p.sourceApp); put("text", p.text)
            put("posted_at", p.postedAt); put("status", MENUNGGU); put("created_at", System.currentTimeMillis())
        },
        SQLiteDatabase.CONFLICT_IGNORE,
    ) != -1L

    fun mark(id: Long, status: String, result: String?) {
        writableDatabase.update("item", ContentValues().apply { put("status", status); put("result", result) }, "id = ?", arrayOf("$id"))
    }

    fun pending(limit: Int = 20) = query("status = ?", arrayOf(MENUNGGU), "id", limit)
    fun recent(limit: Int = 200) = query(null, null, "id desc", limit)

    fun count(status: String): Int =
        readableDatabase.rawQuery("select count(*) from item where status = ?", arrayOf(status)).use { it.moveToFirst(); it.getInt(0) }

    /** Clears finished items: everything with clearAll, otherwise those past the retention. Waiting items stay. */
    fun purge(clearAll: Boolean = false) {
        val before = if (clearAll) Long.MAX_VALUE else System.currentTimeMillis() - KEEP_MS
        writableDatabase.delete("item", "status != ? and created_at < ?", arrayOf(MENUNGGU, "$before"))
    }

    private fun query(where: String?, args: Array<String>?, order: String, limit: Int): List<Item> =
        readableDatabase.query("item", null, where, args, null, null, order, "$limit").use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Item(
                        c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("source_app")),
                        c.getString(c.getColumnIndexOrThrow("text")), c.getString(c.getColumnIndexOrThrow("posted_at")),
                        c.getString(c.getColumnIndexOrThrow("status")), c.getString(c.getColumnIndexOrThrow("result")),
                        c.getLong(c.getColumnIndexOrThrow("created_at")),
                    ),
                )
            }
        }

    companion object {
        @Volatile private var instance: Queue? = null
        fun get(ctx: Context) = instance ?: synchronized(this) { instance ?: Queue(ctx.applicationContext).also { instance = it } }
    }
}
