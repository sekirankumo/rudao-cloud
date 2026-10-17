package com.eaglelan.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

private const val TAG_SEP = "\u001F"

@Entity(tableName = "items", indices = [Index("btime"), Index("isDeleted")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ext: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Double,
    /** Tags joined with U+001F. */
    val tags: String,
    /** Folder ids joined with ",". Empty = uncategorized. */
    val folders: String,
    val star: Int,
    val annotation: String,
    val url: String,
    val btime: Long,
    val mtime: Long,
    val isDeleted: Boolean,
    val hasThumb: Boolean,
)

private val VIDEO_EXTS = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "wmv", "flv", "ts", "3gp")
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic")

val ItemEntity.isVideo get() = ext in VIDEO_EXTS
val ItemEntity.isImage get() = ext in IMAGE_EXTS
val ItemEntity.tagList get() = if (tags.isEmpty()) emptyList() else tags.split(TAG_SEP)
val ItemEntity.aspectRatio
    get() = if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(0.5f, 2f) else 1f

fun ItemDto.toEntity() = ItemEntity(
    id = id, name = name, ext = ext.lowercase(), size = size, width = width, height = height,
    duration = duration, tags = tags.joinToString(TAG_SEP), folders = folders.joinToString(","),
    star = star, annotation = annotation, url = url, btime = btime, mtime = mtime,
    isDeleted = isDeleted, hasThumb = hasThumb,
)

@Entity(
    tableName = "item_folder",
    primaryKeys = ["itemId", "folderId"],
    indices = [Index("folderId")],
)
data class ItemFolder(val itemId: String, val folderId: String)

@Entity(
    tableName = "item_tag",
    primaryKeys = ["itemId", "tag"],
    indices = [Index("tag")],
)
data class ItemTag(val itemId: String, val tag: String)

/** Phone-local favourites; never sent to the computer or written to Eagle. */
@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val itemId: String, val addedAt: Long)

data class FolderCount(val folderId: String, val n: Int)

data class TagCount(val tag: String, val n: Int)

const val UNGROUPED_TAGS = "其他"

/** Eagle tags here look like "组名: 值"; tags without a colon share one "其他" group. */
fun tagGroupOf(tag: String) = if (':' in tag) tag.substringBefore(':').trim() else UNGROUPED_TAGS
fun tagValueOf(tag: String) = if (':' in tag) tag.substringAfter(':').trim() else tag

@Dao
interface ItemDao {
    @RawQuery(observedEntities = [ItemEntity::class, ItemFolder::class, ItemTag::class, Favorite::class])
    fun observe(query: SupportSQLiteQuery): Flow<List<ItemEntity>>

    @Upsert
    suspend fun upsertItems(items: List<ItemEntity>)

    @Query("DELETE FROM item_folder WHERE itemId IN (:ids)")
    suspend fun deleteFolderRefs(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderRefs(refs: List<ItemFolder>)

    @Query("DELETE FROM item_tag WHERE itemId IN (:ids)")
    suspend fun deleteTagRefs(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagRefs(refs: List<ItemTag>)

    @Query("DELETE FROM item_tag")
    suspend fun clearTagRefs()

    @Query(
        "SELECT t.tag AS tag, COUNT(*) AS n FROM item_tag t " +
            "JOIN items i ON i.id = t.itemId WHERE i.isDeleted = 0 GROUP BY t.tag"
    )
    fun tagCounts(): Flow<List<TagCount>>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun localCount(): Int

    @Query("SELECT itemId FROM favorites")
    fun favoriteIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM favorites f JOIN items i ON i.id = f.itemId WHERE i.isDeleted = 0")
    fun countFavorites(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: Favorite)

    @Query("DELETE FROM favorites WHERE itemId = :id")
    suspend fun removeFavorite(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Query("SELECT id FROM items")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<String>)

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM item_folder")
    suspend fun clearFolderRefs()

    @Query(
        "SELECT f.folderId AS folderId, COUNT(*) AS n FROM item_folder f " +
            "JOIN items i ON i.id = f.itemId WHERE i.isDeleted = 0 GROUP BY f.folderId"
    )
    fun folderCounts(): Flow<List<FolderCount>>

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0 AND folders = ''")
    fun countUncategorized(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 1")
    fun countTrash(): Flow<Int>
}

@Database(
    entities = [ItemEntity::class, ItemFolder::class, ItemTag::class, Favorite::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, "library.db")
                // Favourites can't be re-synced from the computer, so every schema change from here on
                // needs a real migration. The destructive fallback only covers the pre-favourites v1.
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigrationFrom(1)
                .build()

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (`itemId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemId`))"
                )
            }
        }
    }
}
