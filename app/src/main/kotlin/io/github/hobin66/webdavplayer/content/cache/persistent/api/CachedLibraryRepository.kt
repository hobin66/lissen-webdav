package io.github.hobin66.webdavplayer.content.cache.persistent.api

import io.github.hobin66.webdavplayer.content.cache.persistent.converter.CachedLibraryEntityConverter
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedLibraryDao
import io.github.hobin66.webdavplayer.lib.domain.Library
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedLibraryRepository
  @Inject
  constructor(
    private val dao: CachedLibraryDao,
    private val converter: CachedLibraryEntityConverter,
  ) {
    suspend fun cacheLibraries(libraries: List<Library>) = dao.updateLibraries(libraries)

    suspend fun deleteAll() = dao.deleteAllLibraries()

    suspend fun fetchLibraries() =
      dao
        .fetchLibraries()
        .map { converter.apply(it) }
  }
