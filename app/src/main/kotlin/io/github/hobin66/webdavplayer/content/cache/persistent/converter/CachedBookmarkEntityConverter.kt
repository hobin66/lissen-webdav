package io.github.hobin66.webdavplayer.content.cache.persistent.converter

import io.github.hobin66.webdavplayer.content.cache.persistent.entity.CachedBookmarkEntity
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.BookmarkSyncState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookmarkEntityConverter
  @Inject
  constructor() {
    fun apply(
      entity: CachedBookmarkEntity,
      syncState: BookmarkSyncState,
    ): Bookmark =
      Bookmark(
        libraryItemId = entity.libraryItemId,
        title = entity.title,
        totalPosition = entity.totalPosition.toDouble(),
        createdAt = entity.createdAt,
        syncState = syncState,
      )
  }
