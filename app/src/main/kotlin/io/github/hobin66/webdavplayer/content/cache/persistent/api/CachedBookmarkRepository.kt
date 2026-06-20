package io.github.hobin66.webdavplayer.content.cache.persistent.api

import io.github.hobin66.webdavplayer.content.cache.persistent.converter.CachedBookmarkEntityConverter
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookmarkDao
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.CachedBookmarkEntity
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.asBookmarkSyncState
import io.github.hobin66.webdavplayer.lib.domain.asInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookmarkRepository
  @Inject
  constructor(
    private val dao: CachedBookmarkDao,
    private val converter: CachedBookmarkEntityConverter,
  ) {
    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> =
      dao
        .fetchByLibraryItemId(libraryItemId)
        .map { converter.apply(entity = it, syncState = it.syncState.asBookmarkSyncState()) }

    suspend fun upsertBookmark(bookmark: Bookmark) {
      dao.upsert(
        CachedBookmarkEntity(
          id = bookmark.id,
          title = bookmark.title,
          libraryItemId = bookmark.libraryItemId,
          createdAt = bookmark.createdAt,
          totalPosition = bookmark.totalPosition.toLong(),
          syncState = bookmark.syncState.asInteger(),
          chapterId = bookmark.chapterId,
          chapterPosition = bookmark.chapterPosition,
        ),
      )
    }

    suspend fun deleteBookmark(bookmarkId: String): Boolean = dao.deleteById(bookmarkId) > 0

    suspend fun deleteAll() {
      dao.deleteAll()
    }
  }
