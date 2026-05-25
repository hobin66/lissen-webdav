package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.grakovne.lissen.content.cache.persistent.entity.BookSkipSettingsEntity

@Dao
interface CachedBookSkipSettingsDao {
  @Query("SELECT * FROM book_skip_settings WHERE bookId = :bookId")
  suspend fun fetchPersistedBookSkipSettings(bookId: String): BookSkipSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPersistedBookSkipSettings(settings: BookSkipSettingsEntity)
}
