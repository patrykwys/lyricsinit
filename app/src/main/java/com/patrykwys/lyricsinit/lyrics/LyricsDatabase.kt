package com.patrykwys.lyricsinit.lyrics

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

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

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE id = :id")
    suspend fun get(id: String): CachedLyrics?

    @Upsert
    suspend fun upsert(entry: CachedLyrics)
}

@Database(entities = [CachedLyrics::class], version = 1, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
}
