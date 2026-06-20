package io.github.hobin66.webdavplayer.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookSkipSettingsEntity

@Dao
interface CachedBookSkipSettingsDao {
  @Query("SELECT * FROM book_skip_settings WHERE bookId = :bookId")
  suspend fun fetchPersistedBookSkipSettings(bookId: String): BookSkipSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPersistedBookSkipSettings(settings: BookSkipSettingsEntity)

  @Query("DELETE FROM book_skip_settings")
  suspend fun deleteAll()
}
