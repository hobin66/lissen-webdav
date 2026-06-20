package io.github.hobin66.webdavplayer.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.channel.common.ChannelProvider
import io.github.hobin66.webdavplayer.common.buildBookmarkTitle
import io.github.hobin66.webdavplayer.content.cache.persistent.LocalCacheRepository
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.BookmarkSyncState
import io.github.hobin66.webdavplayer.lib.domain.CreateBookmarkRequest
import io.github.hobin66.webdavplayer.lib.domain.isSame
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor(
    private val channelProvider: ChannelProvider,
    private val localCacheRepository: LocalCacheRepository,
    private val preferences: WebdavPlayerPreferences,
  ) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun provideBookmarks(libraryItemId: String): List<Bookmark> =
      localCacheRepository
        .fetchBookmarks(libraryItemId)
        .filter { it.syncState != BookmarkSyncState.PENDING_DELETE }
        .sortedByDescending { it.createdAt }
        .fold(mutableListOf()) { acc, b ->
          if (acc.none { it.isSame(b) }) acc.add(b)
          acc
        }

    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val local = localCacheRepository.fetchBookmarks(libraryItemId)

      local
        .asSequence()
        .filter { it.libraryItemId == libraryItemId }
        .filter { it.syncState == BookmarkSyncState.PENDING_CREATE }
        .forEach { pending ->
          channelProvider
            .provideMediaChannel()
            .createBookmark(
              CreateBookmarkRequest(
                title = pending.title,
                time = pending.totalPosition.toInt(),
                libraryItemId = pending.libraryItemId,
              ),
            ).foldAsync(
              onSuccess = { remoteCreated ->
                localCacheRepository.deleteBookmark(pending.libraryItemId, pending.totalPosition)
                localCacheRepository.upsertBookmark(remoteCreated.copy(syncState = BookmarkSyncState.SYNCED))
              },
              onFailure = { Unit },
            )
        }

      local
        .asSequence()
        .filter { it.libraryItemId == libraryItemId }
        .filter { it.syncState == BookmarkSyncState.PENDING_DELETE }
        .forEach { pendingDelete ->
          channelProvider
            .provideMediaChannel()
            .dropBookmark(pendingDelete)
            .foldAsync(
              onSuccess = {
                localCacheRepository.deleteBookmark(pendingDelete.libraryItemId, pendingDelete.totalPosition)
              },
              onFailure = { Unit },
            )
        }

      val remote =
        channelProvider
          .provideMediaChannel()
          .fetchBookmarks(libraryItemId)
          .foldAsync(
            onSuccess = { it },
            onFailure = { return@foldAsync null },
          ) ?: return provideBookmarks(libraryItemId)

      remote.forEach { localCacheRepository.upsertBookmark(it.copy(syncState = BookmarkSyncState.SYNCED)) }

      val afterSyncLocal = localCacheRepository.fetchBookmarks(libraryItemId)

      afterSyncLocal
        .asSequence()
        .filter { it.syncState == BookmarkSyncState.SYNCED }
        .filter { l -> remote.none { r -> r.isSame(l) } }
        .forEach { orphan -> localCacheRepository.deleteBookmark(orphan.libraryItemId, orphan.totalPosition) }

      return provideBookmarks(libraryItemId)
    }

    suspend fun createBookmark(
      chapterTime: Double,
      totalTime: Double,
      libraryItemId: String,
      currentChapter: String,
    ): Bookmark {
      val localDraft =
        Bookmark(
          libraryItemId = libraryItemId,
          title = buildBookmarkTitle(currentChapter, chapterTime),
          totalPosition = totalTime,
          createdAt = System.currentTimeMillis(),
          syncState = BookmarkSyncState.PENDING_CREATE,
        )

      localCacheRepository.upsertBookmark(localDraft)

      scope.launch {
        channelProvider
          .provideMediaChannel()
          .createBookmark(
            CreateBookmarkRequest(
              title = localDraft.title,
              time = totalTime.toInt(),
              libraryItemId = libraryItemId,
            ),
          ).foldAsync(
            onSuccess = { remote ->
              localCacheRepository.upsertBookmark(remote.copy(syncState = BookmarkSyncState.SYNCED))
            },
            onFailure = { /* keep localDraft as-is */ },
          )
      }

      return localDraft
    }

    suspend fun dropBookmark(bookmark: Bookmark) {
      localCacheRepository.upsertBookmark(
        bookmark.copy(syncState = BookmarkSyncState.PENDING_DELETE),
      )

      channelProvider
        .provideMediaChannel()
        .dropBookmark(bookmark)
        .foldAsync(
          onSuccess = { localCacheRepository.deleteBookmark(bookmark.libraryItemId, bookmark.totalPosition) },
          onFailure = { Unit },
        )
    }
  }
