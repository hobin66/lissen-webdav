package io.github.hobin66.webdavplayer.content

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.hobin66.webdavplayer.channel.common.ChannelAuthService
import io.github.hobin66.webdavplayer.channel.common.ChannelProvider
import io.github.hobin66.webdavplayer.channel.common.MediaChannel
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.channel.common.RefreshableChannel
import io.github.hobin66.webdavplayer.channel.webdav.WebdavManageBookItem
import io.github.hobin66.webdavplayer.channel.webdav.WebdavMediaChannel
import io.github.hobin66.webdavplayer.channel.webdav.WebdavRefreshProgress
import io.github.hobin66.webdavplayer.content.cache.persistent.LocalCacheRepository
import io.github.hobin66.webdavplayer.content.cache.temporary.CachedBookmarkProvider
import io.github.hobin66.webdavplayer.content.cache.temporary.CachedCoverProvider
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.Library
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.PagedItems
import io.github.hobin66.webdavplayer.lib.domain.PlaybackProgress
import io.github.hobin66.webdavplayer.lib.domain.RecentBook
import io.github.hobin66.webdavplayer.lib.domain.UserAccount
import io.github.hobin66.webdavplayer.lib.domain.isSame
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.service.PlaybackSnapshotRecord
import io.github.hobin66.webdavplayer.playback.service.PlaybackSnapshotTrigger
import io.github.hobin66.webdavplayer.playback.service.calculateChapterIndex
import io.github.hobin66.webdavplayer.playback.service.canRestoreFromOverallProgress
import io.github.hobin66.webdavplayer.playback.service.shouldUpdateRecentPlaybackSummary
import io.github.hobin66.webdavplayer.playback.BookSkipSettingsStore
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavMediaProvider
  @Inject
  constructor(
    private val preferences: WebdavPlayerPreferences,
    private val channelProvider: ChannelProvider,
    private val localCacheRepository: LocalCacheRepository,
    private val cachedCoverProvider: CachedCoverProvider,
    private val cachedBookmarkProvider: CachedBookmarkProvider,
  ) {
    private val _remoteRefreshVersion = MutableStateFlow(0L)
    val remoteRefreshVersion = _remoteRefreshVersion.asStateFlow()
    private val remoteFileUriCache =
      object : LinkedHashMap<String, Uri>(FILE_URI_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Uri>?): Boolean = size > FILE_URI_CACHE_SIZE
      }

    suspend fun dropBookmark(bookmark: Bookmark) = cachedBookmarkProvider.dropBookmark(bookmark = bookmark)

    suspend fun createBookmark(
      libraryItemId: String,
      chapterId: String,
      chapterPosition: Double,
      totalPosition: Double,
    ): Bookmark? {
      val playingItem = preferences.getPlayingItem() ?: return null

      return cachedBookmarkProvider
        .createBookmark(
          chapterId = chapterId,
          chapterTime = chapterPosition,
          libraryItemId = libraryItemId,
          totalTime = totalPosition,
          currentChapter = playingItem.chapters[calculateChapterIndex(playingItem, totalPosition)].title,
        )
    }

    suspend fun provideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .provideBookmarks(playingItemId)
        .sortedByDescending { it.createdAt }
        .fold(emptyList()) { acc, item -> if (acc.any { it.isSame(item) }) acc else acc + item }

    suspend fun updateAndProvideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .fetchBookmarks(playingItemId)
        .sortedByDescending { it.createdAt }
        .fold(emptyList()) { acc, b -> if (acc.any { it.isSame(b) }) acc else acc + b }

    fun provideFileUri(
      libraryItemId: String,
      chapterId: String,
    ): OperationResult<Uri> {
      Timber.d("Fetching File $libraryItemId and $chapterId URI")

      val result =
        when (preferences.isForceCache()) {
          true -> {
            localCacheRepository
              .provideFileUri(libraryItemId, chapterId)
              ?.let { OperationResult.Success(it) }
              ?: OperationResult.Error(OperationError.InternalError)
          }

          false -> {
            localCacheRepository
              .provideFileUri(libraryItemId, chapterId)
              ?.let { OperationResult.Success(it) }
              ?: provideRemoteFileUri(libraryItemId, chapterId)
          }
        }

      return result
    }

    suspend fun persistPlaybackSnapshot(
      detailedItem: DetailedItem,
      chapterId: String,
      progress: PlaybackProgress,
      trigger: PlaybackSnapshotTrigger,
    ) {
      persistPlaybackSnapshotRecord(
        detailedItem = detailedItem,
        chapterId = chapterId,
        progress = progress,
      )

      if (shouldUpdateRecentPlaybackSummary(trigger)) {
        updateRecentPlaybackSummary(
          detailedItem = detailedItem,
          progress = progress,
        )
      }

      if (detailedItem.canRestoreFromOverallProgress()) {
        localCacheRepository.syncProgress(detailedItem, progress)
      }
    }

    suspend fun fetchBookCover(bookId: String): OperationResult<File> {
      Timber.d("Fetching Cover stream for $bookId")
      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchBookCover(bookId)
        }

        false -> {
          cachedCoverProvider.provideCover(
            channel = providePreferredChannel(),
            itemId = bookId,
          )
        }
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> {
      Timber.d("Searching books with query $query of library: $libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.searchBooks(libraryId = libraryId, query = query)
        }

        false -> {
          providePreferredChannel()
            .searchBooks(
              libraryId = libraryId,
              query = query,
              limit = limit,
            )
        }
      }
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      Timber.d("Fetching page $pageNumber of library: $libraryId")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
        false -> providePreferredChannel().fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
      }
    }

    suspend fun fetchLibraries(): OperationResult<List<Library>> {
      Timber.d("Fetching List of libraries")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchLibraries()
        }

        false -> {
          providePreferredChannel()
            .fetchLibraries()
            .also {
              it.foldAsync(
                onSuccess = { libraries -> localCacheRepository.updateLibraries(libraries) },
                onFailure = {},
              )
            }
        }
      }
    }

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> {
      Timber.d("Fetching Recent books of library $libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchRecentListenedBooks(libraryId)
        }

        false -> {
          providePreferredChannel()
            .fetchRecentListenedBooks(libraryId)
            .map { items -> syncFromLocalProgress(libraryId = libraryId, detailedItems = items) }
        }
      }
    }

    suspend fun fetchBook(
      bookId: String,
      focusChapterId: String? = null,
    ): OperationResult<DetailedItem> {
      Timber.d("Fetching Detailed book info for $bookId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository
            .fetchBook(bookId)
            ?.let { OperationResult.Success(it) }
            ?: OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          providePreferredChannel()
            .fetchBook(bookId, focusChapterId)
            .map { syncFromLocalProgress(it) }
            .map { localCacheRepository.seedPersistedBookSkipSettingsIfMissing(it) }
            .map { trimProgress(it) }
        }
      }
    }

    suspend fun authorize(
      host: String,
      username: String,
      password: String,
      rootPath: String,
    ): OperationResult<UserAccount> {
      Timber.d("Authorizing for $username@$host")
      return provideAuthService().authorize(host, username, password, rootPath) { onPostLogin(host, rootPath, it) }
    }

    suspend fun onPostLogin(
      host: String,
      rootPath: String? = null,
      account: UserAccount,
    ) {
      provideAuthService()
        .persistCredentials(
          host = host,
          username = account.username,
          password = account.password,
          rootPath = rootPath,
        )

      fetchLibraries()
        .fold(
          onSuccess = {
            val preferredLibrary =
              it
                .find { item -> item.id == WebdavMediaChannel.WEBDAV_LIBRARY_ID }
                ?: it.firstOrNull()

            preferredLibrary
              ?.let { library ->
                preferences.savePreferredLibrary(
                  Library(
                    id = library.id,
                    title = library.title,
                    type = library.type,
                  ),
                )
              }
          },
          onFailure = {
            preferences.savePreferredLibrary(
              Library(
                id = WebdavMediaChannel.WEBDAV_LIBRARY_ID,
                title = WebdavMediaChannel.WEBDAV_LIBRARY_TITLE,
                type = LibraryType.LIBRARY,
              ),
            )
          },
        )
    }

    suspend fun clearSessionState() {
      BookSkipSettingsStore.clear()
      preferences.clearPlayingItem()
      localCacheRepository.clearAll()
      cachedCoverProvider.clearCache()

      when (val channel = providePreferredChannel()) {
        is WebdavMediaChannel -> channel.clearSessionState()
      }
    }

    private suspend fun syncFromLocalProgress(
      libraryId: String,
      detailedItems: List<RecentBook>,
    ): List<RecentBook> {
      val localRecentlyBooks =
        localCacheRepository
          .fetchRecentListenedBooks(libraryId)
          .fold(
            onSuccess = { it },
            onFailure = { return@fold detailedItems },
          )

      val syncedRecentlyBooks =
        detailedItems
          .mapNotNull { item -> localRecentlyBooks.find { it.id == item.id }?.let { item to it } }
          .map { (remote, local) ->
            val localTimestamp = local.listenedLastUpdate ?: return@map remote
            val remoteTimestamp = remote.listenedLastUpdate ?: return@map remote

            when (remoteTimestamp > localTimestamp) {
              true -> remote
              false -> local
            }
          }

      return detailedItems
        .map { item ->
          syncedRecentlyBooks
            .find { item.id == it.id }
            ?.let { local -> item.copy(listenedPercentage = local.listenedPercentage) }
            ?: item
        }
    }

    private fun trimProgress(detailedItem: DetailedItem): DetailedItem {
      val totalDuration = detailedItem.chapters.sumOf { it.duration }
      val progress = detailedItem.progress?.currentTime ?: return detailedItem

      return when {
        shouldTrimProgress(totalDuration = totalDuration, progress = progress) -> detailedItem.copy(progress = null)
        else -> detailedItem
      }
    }

    private suspend fun syncFromLocalProgress(detailedItem: DetailedItem): DetailedItem {
      val cachedProgress =
        localCacheRepository
          .fetchPlayingItemProgress(detailedItem.id)
          ?.takeIf { detailedItem.canRestoreFromOverallProgress() }
      val channelProgress = detailedItem.progress

      val updatedProgress =
        listOfNotNull(cachedProgress, channelProgress)
          .maxByOrNull { it.lastUpdate }
          ?: return detailedItem

      Timber.d(
        """
        Merging local playback progress into channel-fetched:
            Channel Progress: $channelProgress
            Cached Progress: $cachedProgress
            Final Progress: $updatedProgress
        """.trimIndent(),
      )

      return detailedItem.copy(progress = updatedProgress)
    }

    private fun persistPlaybackSnapshotRecord(
      detailedItem: DetailedItem,
      chapterId: String,
      progress: PlaybackProgress,
    ) {
      preferences.savePlaybackSnapshot(
        PlaybackSnapshotRecord(
          bookId = detailedItem.id,
          chapterId = chapterId,
          chapterPosition = progress.currentChapterTime,
          totalPosition = progress.currentTotalTime,
          lastUpdated = System.currentTimeMillis(),
        ),
      )
    }

    private fun updateRecentPlaybackSummary(
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
    ) {
      val lastUpdate = System.currentTimeMillis()
      val totalDuration =
        detailedItem.chapters
          .sumOf { it.duration }
          .takeIf { it > 0.0 }

      val listenedPercentage =
        totalDuration
          ?.takeIf { it > 0.0 }
          ?.let { duration -> ((progress.currentTotalTime / duration) * 100).toInt() }
          ?.coerceIn(0, 100)

      val recentItem =
        RecentBook(
          id = detailedItem.id,
          title = detailedItem.title,
          subtitle = detailedItem.subtitle,
          author = detailedItem.author,
          listenedPercentage = listenedPercentage,
          listenedLastUpdate = lastUpdate,
        )

      preferences.saveRecentBooks(
        mergeRecentPlayback(
          existing = preferences.getRecentBooks(),
          latest = recentItem,
          limit = 20,
        ),
      )
    }

    fun fetchConnectionHost() = providePreferredChannel().fetchConnectionHost()

    suspend fun fetchConnectionInfo() = providePreferredChannel().fetchConnectionInfo()

    suspend fun refreshRemoteCache(onProgress: (WebdavRefreshProgress) -> Unit = {}): OperationResult<Unit> {
      val channel = providePreferredChannel()
      val result =
        when {
          channel is WebdavMediaChannel -> channel.refreshRemoteCache(onProgress)
          channel is RefreshableChannel -> channel.refreshRemoteCache()
          else -> OperationResult.Error(OperationError.UnsupportedError)
        }

      if (result is OperationResult.Success) {
        _remoteRefreshVersion.value = System.currentTimeMillis()
      }

      return result
    }

    suspend fun refreshItemCache(itemId: String): OperationResult<Unit> {
      val channel = providePreferredChannel()
      return when (channel is RefreshableChannel) {
        true -> channel.refreshItemCache(itemId)
        false -> OperationResult.Error(OperationError.UnsupportedError)
      }
    }

    suspend fun fetchManageBooks(forceRefresh: Boolean): OperationResult<List<WebdavManageBookItem>> {
      val channel = providePreferredChannel()
      return when (channel) {
        is WebdavMediaChannel -> channel.fetchManageBooks(forceRefresh)
        else -> OperationResult.Error(OperationError.UnsupportedError)
      }
    }

    suspend fun addBookToLibrary(bookId: String): OperationResult<Unit> {
      val channel = providePreferredChannel()
      val result =
        when (channel) {
          is WebdavMediaChannel -> channel.addBookToLibrary(bookId)
          else -> OperationResult.Error(OperationError.UnsupportedError)
        }

      if (result is OperationResult.Success) {
        _remoteRefreshVersion.value = System.currentTimeMillis()
      }

      return result
    }

    suspend fun removeBookFromLibrary(bookId: String): OperationResult<Unit> {
      val channel = providePreferredChannel()
      val result =
        when (channel) {
          is WebdavMediaChannel -> channel.removeBookFromLibrary(bookId)
          else -> OperationResult.Error(OperationError.UnsupportedError)
        }

      if (result is OperationResult.Success) {
        _remoteRefreshVersion.value = System.currentTimeMillis()
      }

      return result
    }

    suspend fun updateBookSkipSettings(
      itemId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ): OperationResult<Unit> =
      runCatching {
        localCacheRepository.updateBookSkipSettings(
          bookId = itemId,
          introSkipSeconds = introSkipSeconds,
          outroSkipSeconds = outroSkipSeconds,
        )
      }.fold(
        onSuccess = { OperationResult.Success(Unit) },
        onFailure = {
          Timber.e(it, "Unable to update local book skip settings for $itemId")
          OperationResult.Error(OperationError.InternalError)
        },
      )

    fun canRefreshRemoteCache(): Boolean = providePreferredChannel() is RefreshableChannel

    fun provideAuthService(): ChannelAuthService = channelProvider.provideChannelAuth()

    fun providePreferredChannel(): MediaChannel = channelProvider.provideMediaChannel()

    private fun provideRemoteFileUri(
      libraryItemId: String,
      chapterId: String,
    ): OperationResult<Uri> {
      val cacheKey = remoteFileUriCacheKey(libraryItemId, chapterId)

      synchronized(remoteFileUriCache) {
        remoteFileUriCache[cacheKey]?.let { return OperationResult.Success(it) }
      }

      val result =
        providePreferredChannel()
          .provideFileUri(libraryItemId, chapterId)
          .let { OperationResult.Success(it) }

      if (result.data != Uri.EMPTY) {
        synchronized(remoteFileUriCache) {
          remoteFileUriCache[cacheKey] = result.data
        }
      }

      return result
    }

    private fun remoteFileUriCacheKey(
      libraryItemId: String,
      chapterId: String,
    ): String =
      buildString {
        append(preferences.getHost().orEmpty())
        append("::")
        append(preferences.getWebdavRoot().orEmpty())
        append("::")
        append(libraryItemId)
        append("::")
        append(chapterId)
      }

    companion object {
      private const val FILE_URI_CACHE_SIZE = 256
    }
  }
