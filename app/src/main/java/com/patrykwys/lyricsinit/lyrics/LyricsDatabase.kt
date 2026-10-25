package com.patrykwys.lyricsinit.lyrics

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Raw LRCLIB result for a track. A row with no lyrics at all records a "not found". */
@Entity(tableName = "lyrics")
data class CachedLyrics(
    @PrimaryKey val id: String,
    val synced: String?,
    val plain: String?,
    val instrumental: Boolean,
    val fetchedAt: Long,
) {
    val hasContent: Boolean get() = instrumental || !synced.isNullOrBlank() || !plain.isNullOrBlank()
}

/** Genius lyrics text for a track, section headers included. A null [text] records a "not found". */
@Entity(tableName = "genius_lyrics")
data class CachedGeniusLyrics(
    @PrimaryKey val id: String,
    val text: String?,
    val fetchedAt: Long,
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE id = :id")
    suspend fun get(id: String): CachedLyrics?

    @Upsert
    suspend fun upsert(entry: CachedLyrics)
}

@Dao
interface GeniusLyricsDao {
    @Query("SELECT * FROM genius_lyrics WHERE id = :id")
    suspend fun get(id: String): CachedGeniusLyrics?

    @Upsert
    suspend fun upsert(entry: CachedGeniusLyrics)
}

@Database(entities = [CachedLyrics::class, CachedGeniusLyrics::class], version = 2, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
    abstract fun geniusLyricsDao(): GeniusLyricsDao

    companion object {
        /** Keeps the lyrics cache, which is what makes songs work without signal. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `genius_lyrics` " +
                        "(`id` TEXT NOT NULL, `text` TEXT, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
