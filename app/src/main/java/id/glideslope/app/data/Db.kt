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
)

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val id: String, val name: String, val kind: String, val isSystem: Boolean, val archivedAt: String?)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val kind: String)

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
                  a.name as accountName, c.name as categoryName
           from transactions t left join accounts a on a.id = t.accountId left join categories c on c.id = t.categoryId
           where t.deletedAt is null and t.status != 'ignored'
           order by t.occurredAt desc limit :limit""",
    )
    fun recent(limit: Int = 300): Flow<List<TxListItem>>

    @Query("select count(*) from transactions where status = 'needs_review' and deletedAt is null")
    fun needsReviewCount(): Flow<Int>

    @Query("select * from accounts where not isSystem and archivedAt is null order by name")
    fun activeAccounts(): Flow<List<AccountEntity>>
}

@Database(entities = [SyncRow::class, TxEntity::class, AccountEntity::class, CategoryEntity::class, MetaEntity::class], version = 1)
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
