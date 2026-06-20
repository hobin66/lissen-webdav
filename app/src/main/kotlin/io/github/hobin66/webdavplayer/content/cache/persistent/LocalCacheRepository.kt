package io.github.hobin66.webdavplayer.content.cache.persistent

import android.net.Uri
import androidx.core.net.toFile
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.content.cache.persistent.api.CachedBookRepository
import io.github.hobin66.webdavplayer.content.cache.persistent.api.CachedBookSkipSettingsRepository
import io.github.hobin66.webdavplayer.content.cache.persistent.api.CachedBookmarkRepository
import io.github.hobin66.webdavplayer.content.cache.persistent.api.CachedLibraryRepository
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.Library
import io.github.hobin66.webdavplayer.lib.domain.MediaProgress
import io.github.hobin66.webdavplayer.lib.domain.PagedItems
import io.github.hobin66.webdavplayer.lib.domain.PlaybackProgress
import io.github.hobin66.webdavplayer.lib.domain.RecentBook
import io.github.hobin66.webdavplayer.playback.service.calculateChapterIndex
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheRepository
  @Inject
  constructor(
    private val cachedBookRepository: CachedBookRepository,
    private val cachedBookSkipSettingsRepository: CachedBookSkipSettingsRepository,
    private val cachedLibraryRepository: CachedLibraryRepository,
    private val cachedBookmarkRepository: CachedBookmarkRepository,
  ) {
    fun provideFileUri(
      libraryItemId: String,
      fileId: String,
    ): Uri? =
      cachedBookRepository
        .provideFileUri(libraryItemId, fileId)
        .takeIf { it.toFile().exists() }

    /**
     * For the local cache we're avoiding to create intermediary entity like Session and using BookId
     * as a Playback Session Key
     */
    suspend fun syncProgress(
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
    ): OperationResult<Unit> {
      cachedBookRepository.syncProgress(detailedItem, progress)
      return OperationResult.Success(Unit)
    }

    fun fetchBookCover(bookId: String): OperationResult<File> {
      val coverFile = cachedBookRepository.provideBookCover(bookId)

      return when (coverFile.exists()) {
        true -> OperationResult.Success(coverFile)
        false -> OperationResult.Error(OperationError.InternalError)
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .searchBooks(libraryId = libraryId, query = query)
        .let { OperationResult.Success(it) }

    suspend fun fetchDetailedItems(): OperationResult<PagedItems<DetailedItem>> {
      val items =
        buildList {
          cachedBookRepository
            .fetchCachedItems()
            .forEach { add(applyPersistedBookSkipSettings(it)) }
        }

      return OperationResult
        .Success(
          PagedItems(
            items = items,
            currentPage = 0,
            totalItems = cachedBookRepository.countCachedItems(),
          ),
        )
    }

    suspend fun fetchDetailedItems(
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<DetailedItem>> {
      val items =
        buildList {
          cachedBookRepository
            .fetchCachedItems(pageNumber = pageNumber, pageSize = pageSize)
            .forEach { add(applyPersistedBookSkipSettings(it)) }
        }

      return OperationResult
        .Success(
          PagedItems(
            items = items,
            currentPage = pageNumber,
            totalItems = cachedBookRepository.countCachedItems(),
          ),
        )
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      val books =
        cachedBookRepository
          .fetchBooks(pageNumber = pageNumber, pageSize = pageSize, libraryId = libraryId)

      return OperationResult
        .Success(
          PagedItems(
            items = books,
            currentPage = pageNumber,
            totalItems = cachedBookRepository.countBooks(libraryId),
          ),
        )
    }

    suspend fun fetchLibraries(): OperationResult<List<Library>> =
      cachedLibraryRepository
        .fetchLibraries()
        .let { OperationResult.Success(it) }

    suspend fun updateLibraries(libraries: List<Library>) {
      cachedLibraryRepository.cacheLibraries(libraries)
    }

    suspend fun updateBookSkipSettings(
      bookId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ) {
      cachedBookSkipSettingsRepository.updateBookSkipSettings(bookId, introSkipSeconds, outroSkipSeconds)
      cachedBookRepository.updateBookSkipSettings(bookId, introSkipSeconds, outroSkipSeconds)
    }

    suspend fun fetchPlayingItemProgress(itemId: String) =
      cachedBookRepository
        .fetchMediaProgress(itemId)

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> =
      cachedBookRepository
        .fetchRecentBooks(libraryId)
        .let { OperationResult.Success(it) }

    suspend fun fetchLatestUpdate(libraryId: String) = cachedBookRepository.fetchLatestUpdate(libraryId)

    /**
     * Fetches a detailed book item by its ID from the cached repository.
     * If the book is not found in the cache, returns `null`.
     *
     * The method ensures that the book's playback position points to an available chapter:
     * - If the current chapter is available, the cached book is returned as is.
     * - If the current chapter is unavailable, the playback progress is adjusted to the first available chapter.
     *
     * @param bookId the unique identifier of the book to fetch.
     * @return the detailed book item with updated playback progress if necessary,
     *         or `null` if the book is not found in the cache.
     */
    suspend fun fetchBook(bookId: String): DetailedItem? {
      val cachedBook =
        cachedBookRepository
          .fetchBook(bookId)
          ?: return null

      val cachedPosition =
        cachedBook
          .progress
          ?.currentTime
          ?: 0.0

      val currentChapter = calculateChapterIndex(cachedBook, cachedPosition)

      val adjustedBook =
        when (currentChapter in cachedBook.chapters.indices && cachedBook.chapters[currentChapter].available) {
          true -> {
            cachedBook
          }

          false -> {
            cachedBook
              .copy(
                progress =
                  MediaProgress(
                    currentTime =
                      cachedBook.chapters
                        .firstOrNull { it.available }
                        ?.start
                        ?: return null,
                    isFinished = false,
                    lastUpdate = 946728000000, // 2000-01-01T12:00
                  ),
              )
          }
        }

      return seedPersistedBookSkipSettingsIfMissing(adjustedBook)
    }

    suspend fun seedPersistedBookSkipSettingsIfMissing(book: DetailedItem): DetailedItem =
      cachedBookSkipSettingsRepository.seedPersistedBookSkipSettingsIfMissing(book)

    suspend fun applyPersistedBookSkipSettings(book: DetailedItem): DetailedItem =
      cachedBookSkipSettingsRepository.applyPersistedBookSkipSettings(book)

    suspend fun fetchBookmarks(libraryItemId: String) =
      cachedBookmarkRepository
        .fetchBookmarks(libraryItemId)

    suspend fun upsertBookmark(bookmark: Bookmark) {
      cachedBookmarkRepository.upsertBookmark(bookmark)
    }

    suspend fun deleteBookmark(
      libraryItemId: String,
      totalPosition: Double,
    ) {
      cachedBookmarkRepository.deleteBookmark(libraryItemId, totalPosition)
    }

    suspend fun clearAll() {
      cachedBookmarkRepository.deleteAll()
      cachedLibraryRepository.deleteAll()
      cachedBookSkipSettingsRepository.deleteAll()
      cachedBookRepository.deleteAll()
    }
  }
