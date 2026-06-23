package io.github.hobin66.webdavplayer.content.cache.persistent.api

import android.net.Uri
import androidx.core.net.toUri
import io.github.hobin66.webdavplayer.common.LibraryOrderingDirection
import io.github.hobin66.webdavplayer.common.LibraryOrderingOption
import io.github.hobin66.webdavplayer.content.cache.persistent.OfflineBookStorageProperties
import io.github.hobin66.webdavplayer.content.cache.persistent.converter.CachedBookEntityConverter
import io.github.hobin66.webdavplayer.content.cache.persistent.converter.CachedBookEntityDetailedConverter
import io.github.hobin66.webdavplayer.content.cache.persistent.converter.CachedBookEntityRecentConverter
import io.github.hobin66.webdavplayer.content.cache.persistent.converter.MediaProgressEntityConverter
import io.github.hobin66.webdavplayer.content.cache.persistent.dao.CachedBookDao
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.MediaProgressEntity
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.MediaProgress
import io.github.hobin66.webdavplayer.lib.domain.PlaybackProgress
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import io.github.hobin66.webdavplayer.lib.domain.RecentBook
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookRepository
  @Inject
  constructor(
    private val bookDao: CachedBookDao,
    private val properties: OfflineBookStorageProperties,
    private val cachedBookEntityConverter: CachedBookEntityConverter,
    private val cachedBookEntityDetailedConverter: CachedBookEntityDetailedConverter,
    private val cachedBookEntityRecentConverter: CachedBookEntityRecentConverter,
    private val mediaProgressEntityConverter: MediaProgressEntityConverter,
    private val preferences: WebdavPlayerPreferences,
  ) {
    fun provideFileUri(
      bookId: String,
      fileId: String,
    ): Uri =
      properties
        .provideMediaCachePatch(bookId, fileId)
        .toUri()

    fun provideBookCover(bookId: String): File = properties.provideBookCoverPath(bookId)

    suspend fun removeBook(bookId: String) {
      bookDao
        .fetchBook(bookId)
        ?.let {
          bookDao.deleteMediaProgress(it.id)
          bookDao.deleteBook(it)
        }
    }

    suspend fun cacheBook(
      book: DetailedItem,
      fetchedChapters: List<PlayingChapter>,
      droppedChapters: List<PlayingChapter>,
    ) {
      bookDao.upsertCachedBook(book, fetchedChapters, droppedChapters)
    }

    suspend fun updateBookSkipSettings(
      bookId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ) {
      bookDao.updateBookSkipSettings(bookId, introSkipSeconds, outroSkipSeconds)
    }

    fun provideCacheState(bookId: String) = bookDao.isBookCached(bookId)

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ) = bookDao.isBookChapterCached(bookId, chapterId)

    suspend fun fetchCachedItems() =
      bookDao
        .fetchCachedItems()
        .map { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun fetchCachedItems(
      pageSize: Int,
      pageNumber: Int,
    ) = bookDao
      .fetchCachedItems(pageSize = pageSize, pageNumber = pageNumber)
      .map { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun countCachedItems(): Int = bookDao.fetchCachedItemsCount()

    suspend fun fetchLatestUpdate(libraryId: String) = bookDao.fetchLatestUpdate(libraryId)

    suspend fun fetchBooks(
      libraryId: String,
      pageNumber: Int,
      pageSize: Int,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        FetchRequestBuilder()
          .libraryId(libraryId)
          .pageNumber(pageNumber)
          .pageSize(pageSize)
          .orderField(option)
          .orderDirection(direction)
          .hideCompleted(preferences.getHideCompleted())
          .build()

      return bookDao
        .fetchCachedBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun countBooks(libraryId: String): Int = bookDao.countCachedBooks(libraryId = libraryId)

    suspend fun searchBooks(
      libraryId: String,
      query: String,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        SearchRequestBuilder()
          .searchQuery(query)
          .libraryId(libraryId)
          .orderField(option)
          .orderDirection(direction)
          .build()

      return bookDao
        .searchBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun fetchRecentBooks(libraryId: String): List<RecentBook> {
      val recentBooks =
        bookDao.fetchRecentlyListenedCachedBooks(
          libraryId = libraryId,
        )

      val progress =
        recentBooks
          .map { it.id }
          .mapNotNull { bookDao.fetchMediaProgress(it) }
          .associate { it.bookId to (it.lastUpdate to it.currentTime) }

      return recentBooks
        .map { cachedBookEntityRecentConverter.apply(it, progress[it.id]) }
    }

    suspend fun fetchBook(bookId: String): DetailedItem? =
      bookDao
        .fetchCachedBook(bookId)
        ?.let { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun fetchMediaProgress(playingItemId: String) =
      bookDao
        .fetchMediaProgress(playingItemId)
        ?.let { mediaProgressEntityConverter.apply(it) }

    suspend fun fetchAllMediaProgress(): Map<String, MediaProgress> =
      bookDao
        .fetchAllMediaProgress()
        .associate { it.bookId to mediaProgressEntityConverter.apply(it) }

    suspend fun updateMediaProgress(
      bookId: String,
      progress: MediaProgress?,
    ) {
      if (progress == null) {
        bookDao.deleteMediaProgress(bookId)
        return
      }

      bookDao.upsertMediaProgress(
        MediaProgressEntity(
          bookId = bookId,
          currentTime = progress.currentTime,
          isFinished = progress.isFinished,
          lastUpdate = progress.lastUpdate,
        ),
      )
    }

    suspend fun syncProgress(
      playingItem: DetailedItem,
      progress: PlaybackProgress,
    ) {
      val entity =
        MediaProgressEntity(
          bookId = playingItem.id,
          currentTime = progress.currentTotalTime,
          isFinished = progress.currentTotalTime == playingItem.chapters.sumOf { it.duration },
          lastUpdate = Instant.now().toEpochMilli(),
        )

      bookDao.upsertMediaProgress(entity)
    }

    suspend fun deleteAll() {
      bookDao.deleteAllMediaProgress()
      bookDao.deleteAllBookChapters()
      bookDao.deleteAllBookFiles()
      bookDao.deleteAllBooks()
      properties.provideBookCacheFolder().deleteRecursively()
    }

    private fun buildOrdering(): Pair<String, String> {
      val option =
        when (preferences.getLibraryOrdering().option) {
          LibraryOrderingOption.TITLE -> "title"
          LibraryOrderingOption.AUTHOR -> "author"
          LibraryOrderingOption.CREATED_AT -> "createdAt"
          LibraryOrderingOption.UPDATED_AT -> "updatedAt"
        }

      val direction =
        when (preferences.getLibraryOrdering().direction) {
          LibraryOrderingDirection.ASCENDING -> "asc"
          LibraryOrderingDirection.DESCENDING -> "desc"
        }

      return option to direction
    }
  }
