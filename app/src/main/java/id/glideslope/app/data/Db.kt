// The local copy of the owner's data (F-14.1, ADR 0007): every screen reads from here; the network only updates it.
// Encrypted with SQLCipher; the random passphrase is wrapped by a Keystore key and kept out of backups.
// Every pulled row is kept as JSON in sync_rows; the tables screens query also get a typed copy.
package id.glideslope.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

@Entity(tableName = "sync_rows", primaryKeys = ["tbl", "key"])
data class SyncRow(val tbl: String, val key: String, val json: String)

@Entity(tableName = "transactions")
data class TxEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val direction: String,
    val amount: Long,
    val occurredAt: String,
    val kind: String,
    val status: String,
    val categoryId: String?,
    val merchantRaw: String?,
    val note: String?,
    val isOneOff: Boolean,
    val deletedAt: String?,
    val updatedAt: String? = null,
    val pending: Boolean = false, // written on the phone, not yet accepted by the server
)

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val id: String, val name: String, val kind: String, val isSystem: Boolean, val archivedAt: String?)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val kind: String)

/** F-14.2 outbox: one row per change made on the phone, sent in order. `localIds` are optimistic rows to drop once applied. */
@Entity(tableName = "outbox", indices = [androidx.room.Index(value = ["opId"], unique = true)])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val opId: String,
    val type: String,
    val payload: String,
    val status: String = "menunggu", // menunggu | ditolak | konflik
    val error: String? = null,
    val localIds: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

data class TxListItem(
    val id: String,
    val amount: Long,
    val direction: String,
    val kind: String,
    val status: String,
    val occurredAt: String,
    val merchantRaw: String?,
    val note: String?,
    val accountName: String?,
    val categoryName: String?,
    val pending: Boolean,
)

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun row(r: SyncRow)
    @Query("delete from sync_rows where tbl = :tbl and `key` = :key") suspend fun deleteRow(tbl: String, key: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun tx(t: TxEntity)
    @Query("delete from transactions where id = :id") suspend fun deleteTx(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun account(a: AccountEntity)
    @Query("delete from accounts where id = :id") suspend fun deleteAccount(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun category(c: CategoryEntity)
    @Query("delete from categories where id = :id") suspend fun deleteCategory(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun meta(m: MetaEntity)
    @Query("select value from meta where key = :key") suspend fun meta(key: String): String?
    @Query("select value from meta where key = :key") fun metaFlow(key: String): Flow<String?>
    @Query("delete from sync_rows") suspend fun clearRows()
    @Query("delete from transactions") suspend fun clearTx()
    @Query("delete from accounts") suspend fun clearAccounts()
    @Query("delete from categories") suspend fun clearCategories()
    @Query("delete from meta") suspend fun clearMeta()

    @Query(
        """select t.id, t.amount, t.direction, t.kind, t.status, t.occurredAt, t.merchantRaw, t.note,
                  a.name as accountName, c.name as categoryName, t.pending
           from transactions t left join accounts a on a.id = t.accountId left join categories c on c.id = t.categoryId
           where t.deletedAt is null and t.status != 'ignored'
           order by t.occurredAt desc limit :limit""",
    )
    fun recent(limit: Int = 300): Flow<List<TxListItem>>

    @Query("select count(*) from transactions where status = 'needs_review' and deletedAt is null")
    fun needsReviewCount(): Flow<Int>

    @Query("select * from accounts where not isSystem and archivedAt is null order by name")
    fun activeAccounts(): Flow<List<AccountEntity>>

    @Query("select * from categories where kind = :kind order by name")
    fun categories(kind: String): Flow<List<CategoryEntity>>

    @Query("select json from sync_rows where tbl = :tbl")
    fun rows(tbl: String): Flow<List<String>>

    @Query("select json from sync_rows where tbl = :tbl and `key` = :key")
    suspend fun row(tbl: String, key: String): String?

    @Query("select json from sync_rows where tbl = :tbl and `key` in (:keys)")
    fun rowsByKey(tbl: String, keys: List<String>): Flow<List<String>>

    /** Rows of a child table that mention one parent id (transaction_labels keys, evidence rows). */
    @Query("select json from sync_rows where tbl = :tbl and (`key` like :id || ':%' or json like '%' || :id || '%')")
    fun rowsMentioning(tbl: String, id: String): Flow<List<String>>

    @Query("select * from transactions where id = :id")
    suspend fun txById(id: String): TxEntity?

    @Query("select * from transactions where id = :id")
    fun txFlow(id: String): Flow<TxEntity?>

    @Query("select * from transactions where status = 'needs_review' and deletedAt is null order by occurredAt desc")
    fun reviewQueue(): Flow<List<TxEntity>>

    @Query("select * from accounts")
    fun allAccounts(): Flow<List<AccountEntity>>

    @Query("select * from categories")
    fun allCategories(): Flow<List<CategoryEntity>>

    @Insert suspend fun outbox(o: OutboxEntity)
    @Query("select * from outbox where status = 'menunggu' order by seq limit 50") suspend fun outboxPending(): List<OutboxEntity>
    @Query("select * from outbox where status != 'menunggu' order by seq") fun outboxProblems(): Flow<List<OutboxEntity>>
    @Query("select count(*) from outbox where status = 'menunggu'") fun outboxCount(): Flow<Int>
    @Query("delete from outbox where opId = :opId") suspend fun outboxDone(opId: String)
    @Query("update outbox set status = :status, error = :error where opId = :opId") suspend fun outboxMark(opId: String, status: String, error: String?)
}

@Database(entities = [SyncRow::class, TxEntity::class, AccountEntity::class, CategoryEntity::class, MetaEntity::class, OutboxEntity::class], version = 2)
abstract class Db : RoomDatabase() {
    abstract fun sync(): SyncDao

    companion object {
        @Volatile private var instance: Db? = null

        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: open(ctx.applicationContext).also { instance = it }
        }

        private fun open(ctx: Context): Db {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(ctx, Db::class.java, "glideslope.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase(ctx)))
                // v1 held only pulled data (no outbox yet): rebuilding it is a fresh pull, nothing of the owner's is lost
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
        }

        private fun passphrase(ctx: Context): ByteArray {
            val f = File(ctx.noBackupFilesDir, "db.key")
            if (f.exists()) return Crypto.decrypt(KEY_ALIAS, f.readBytes())
            val p = ByteArray(32).also { SecureRandom().nextBytes(it) }
            f.writeBytes(Crypto.encrypt(KEY_ALIAS, p))
            return p
        }

        /** Logout or "Lupakan perangkat": the database and its key are gone; nothing readable is left. */
        fun wipe(ctx: Context) {
            synchronized(this) { instance?.close(); instance = null }
            ctx.deleteDatabase("glideslope.db")
            File(ctx.noBackupFilesDir, "db.key").delete()
            Crypto.deleteKey(KEY_ALIAS)
        }

        private const val KEY_ALIAS = "glideslope-db"
    }
}
