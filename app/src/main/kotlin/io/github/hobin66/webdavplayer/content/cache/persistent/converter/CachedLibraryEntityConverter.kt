package io.github.hobin66.webdavplayer.content.cache.persistent.converter

import io.github.hobin66.webdavplayer.content.cache.persistent.entity.CachedLibraryEntity
import io.github.hobin66.webdavplayer.lib.domain.Library
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedLibraryEntityConverter
  @Inject
  constructor() {
    fun apply(entity: CachedLibraryEntity): Library =
      Library(
        id = entity.id,
        title = entity.title,
        type = entity.type,
      )
  }
