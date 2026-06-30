package io.github.hobin66.webdavplayer.content.cache.persistent

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookDao
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookSkipSettingsDao
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookmarkDao
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedLibraryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalCacheModule {
  private const val DATABASE_NAME = "webdav_player_local_cache_storage"

  @Provides
  @Singleton
  fun provideAppDatabase(
    @ApplicationContext context: Context,
  ): LocalCacheStorage {
    val builder =
      Room.databaseBuilder(
        context = context,
        klass = LocalCacheStorage::class.java,
        name = DATABASE_NAME,
      )
    builder.addMigrations(LocalCacheStorage.MIGRATION_1_2)
    return builder.build()
  }

  @Provides
  @Singleton
  fun provideCachedBookDao(appDatabase: LocalCacheStorage): CachedBookDao = appDatabase.cachedBookDao()

  @Provides
  @Singleton
  fun provideCachedBookSkipSettingsDao(appDatabase: LocalCacheStorage): CachedBookSkipSettingsDao = appDatabase.cachedBookSkipSettingsDao()

  @Provides
  @Singleton
  fun provideCachedBookmarkDao(appDatabase: LocalCacheStorage): CachedBookmarkDao = appDatabase.cachedBookmarkDao()

  @Provides
  @Singleton
  fun provideCachedLibraryDao(appDatabase: LocalCacheStorage): CachedLibraryDao = appDatabase.cachedLibraryDao()
}
