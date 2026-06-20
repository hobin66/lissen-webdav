package io.github.hobin66.webdavplayer.content.cache.persistent

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookDao
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookSkipSettingsDao
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookmarkDao
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedLibraryDao
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookChapterEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookFileEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookSkipSettingsEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.CachedBookmarkEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.CachedLibraryEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.MediaProgressEntity

@Database(
  entities = [
    BookEntity::class,
    BookFileEntity::class,
    BookChapterEntity::class,
    BookSkipSettingsEntity::class,
    MediaProgressEntity::class,
    CachedLibraryEntity::class,
    CachedBookmarkEntity::class,
  ],
  version = 2,
  exportSchema = true,
)
abstract class LocalCacheStorage : RoomDatabase() {
  abstract fun cachedBookDao(): CachedBookDao

  abstract fun cachedBookSkipSettingsDao(): CachedBookSkipSettingsDao

  abstract fun cachedBookmarkDao(): CachedBookmarkDao

  abstract fun cachedLibraryDao(): CachedLibraryDao
}
