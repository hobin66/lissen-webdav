package io.github.hobin66.webdavplayer.channel.webdav

import android.net.Uri
import io.github.hobin66.webdavplayer.channel.common.ConnectionHost
import io.github.hobin66.webdavplayer.channel.common.ConnectionInfo
import io.github.hobin66.webdavplayer.channel.common.MediaChannel
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.channel.common.RefreshableChannel
import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookDetailCache
import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookIndexEntry
import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavPersistentCache
import io.github.hobin66.webdavplayer.channel.webdav.client.ConditionalFetchStatus
import io.github.hobin66.webdavplayer.channel.webdav.client.ConditionalTextResponse
import io.github.hobin66.webdavplayer.channel.webdav.client.WebdavClient
import io.github.hobin66.webdavplayer.channel.webdav.model.WebdavBookMetadata
import io.github.hobin66.webdavplayer.channel.webdav.model.WebdavPlaybackProgress
import io.github.hobin66.webdavplayer.channel.webdav.model.WebdavResource
import io.github.hobin66.webdavplayer.common.LibraryOrderingConfiguration
import io.github.hobin66.webdavplayer.common.LibraryOrderingDirection
import io.github.hobin66.webdavplayer.common.LibraryOrderingOption
import io.github.hobin66.webdavplayer.common.moshi
import io.github.hobin66.webdavplayer.content.cache.temporary.ShortTermCacheStorageProperties
import io.github.hobin66.webdavplayer.content.fallbackRecentPlayback
import io.github.hobin66.webdavplayer.lib.domain.Book
import io.github.hobin66.webdavplayer.lib.domain.BookFile
import io.github.hobin66.webdavplayer.lib.domain.Bookmark
import io.github.hobin66.webdavplayer.lib.domain.CreateBookmarkRequest
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.Library
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.PagedItems
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import io.github.hobin66.webdavplayer.lib.domain.RecentBook
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavMediaChannel
  @Inject
  constructor(
    private val preferences: WebdavPlayerPreferences,
    private val webdavClient: WebdavClient,
    private val persistentCache: WebdavPersistentCache,
    private val shortTermCacheStorageProperties: ShortTermCacheStorageProperties,
  ) : MediaChannel,
    RefreshableChannel {
    private val metadataAdapter = moshi.adapter(WebdavBookMetadata::class.java)
    private val metadataMutationMutex = Mutex()

    @Volatile
    private var cachedBooks: Map<String, IndexedBook> = emptyMap()

    @Volatile
    private var addedBooksSortedCache: Pair<Map<String, IndexedBook>, List<Book>>? = null

    override fun getLibraryType(): LibraryType = LibraryType.LIBRARY

    override fun provideFileUri(
      libraryItemId: String,
      fileId: String,
    ): Uri {
      val relativePath = resolveWebdavFileRelativePath(fileId) ?: return Uri.EMPTY
      return webdavClient.resolveUri(relativePath) ?: Uri.EMPTY
    }

    override suspend fun fetchBookCover(
      bookId: String,
      width: Int?,
    ): OperationResult<okio.Buffer> {
      val entry = ensureIndexedBooks()[bookId] ?: return OperationResult.Error(OperationError.NotFoundError)
      if (shouldSkipWebdavCoverLookup(entry.toIndexEntry())) {
        return OperationResult.Error(OperationError.NotFoundError)
      }

      val uniqueCandidates =
        buildWebdavCoverCandidates(
          preferredCoverName = entry.metadata.coverOrDefault(),
          resolvedCoverName = entry.resolvedCoverName,
        )

      var lastError: OperationResult.Error<*>? = null

      uniqueCandidates.forEach { coverName ->
        val candidatePath = "${entry.directory.relativePath}/$coverName"

        when (val result = webdavClient.fetchBinary(relativePath = candidatePath)) {
          is OperationResult.Success -> {
            if (entry.resolvedCoverName != coverName || entry.isCoverMissing) {
              updateIndexedBook(bookId) { markResolvedWebdavCover(it, coverName) }
            }
            return result
          }

          is OperationResult.Error -> {
            if (result.code != OperationError.NotFoundError) {
              lastError = result
            }
          }
        }
      }

      updateIndexedBook(bookId, ::markMissingWebdavCover)
      return lastError?.let { OperationResult.Error(it.code) } ?: OperationResult.Error(OperationError.NotFoundError)
    }

    override suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      val allBooks = addedBooksSorted(ensureIndexedBooks())

      val from = pageNumber * pageSize
      val to = minOf(from + pageSize, allBooks.size)
      val pageItems =
        when (from >= allBooks.size) {
          true -> emptyList()
          false -> allBooks.subList(from, to)
        }

      return OperationResult.Success(
        PagedItems(
          items = pageItems,
          currentPage = pageNumber,
          totalItems = allBooks.size,
        ),
      )
    }

    override suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> {
      if (query.isBlank()) {
        return OperationResult.Success(emptyList())
      }

      val lowered = query.lowercase()
      val result =
        addedBooksSorted(ensureIndexedBooks())
          .asSequence()
          .filter { book ->
            book.title.lowercase().contains(lowered) ||
              (book.author?.lowercase()?.contains(lowered) == true)
          }.take(limit)
          .toList()

      return OperationResult.Success(result)
    }

    private fun addedBooksSorted(books: Map<String, IndexedBook>): List<Book> {
      val cached = addedBooksSortedCache
      if (cached != null && cached.first === books) {
        return cached.second
      }
      val ordering = preferences.getLibraryOrdering()
      val sorted =
        books
          .values
          .asSequence()
          .filter { it.isAdded }
          .sortedWith(indexedBookComparator(ordering))
          .map {
            Book(
              id = it.metadata.id,
              subtitle = null,
              series = null,
              title = it.metadata.title,
              author = it.metadata.authorOrNull(),
            )
          }.toList()
      addedBooksSortedCache = books to sorted
      return sorted
    }

    suspend fun fetchManageBooks(forceRefresh: Boolean): OperationResult<List<WebdavManageBookItem>> {
      val fallbackIndexed =
        when {
          cachedBooks.isNotEmpty() -> cachedBooks
          else -> loadPersistedIndex()
        }

      if (!forceRefresh && fallbackIndexed.isNotEmpty()) {
        return OperationResult.Success(fallbackIndexed.toManageItems())
      }

      return rebuildIndexStrict(force = true).fold(
        onSuccess = { OperationResult.Success(it.toManageItems()) },
        onFailure = {
          if (fallbackIndexed.isNotEmpty()) {
            OperationResult.Success(fallbackIndexed.toManageItems())
          } else {
            OperationResult.Error(it.code, it.message)
          }
        },
      )
    }

    suspend fun addBookToLibrary(bookId: String): OperationResult<Unit> {
      val indexed = ensureManageIndexedBooks(forceRefresh = false)
      if (indexed.containsKey(bookId).not()) {
        return OperationResult.Error(OperationError.NotFoundError)
      }

      updateIndexedBook(bookId, ::markBookAdded)
      return OperationResult.Success(Unit)
    }

    suspend fun removeBookFromLibrary(bookId: String): OperationResult<Unit> {
      val indexed = ensureManageIndexedBooks(forceRefresh = false)
      if (indexed.containsKey(bookId).not()) {
        return OperationResult.Error(OperationError.NotFoundError)
      }

      updateIndexedBook(bookId, ::markBookRemoved)
      return OperationResult.Success(Unit)
    }

    suspend fun uploadPlaybackProgress(
      progressByBookId: Map<String, WebdavPlaybackProgress>,
      onProgress: (WebdavRefreshProgress) -> Unit = {},
    ): OperationResult<Unit> =
      metadataMutationMutex.withLock {
        val books = ensureManageIndexedBooksLocked(forceRefresh = false).values.filter { it.isAdded }
        val initialProgress = WebdavRefreshProgress.start(totalBooks = books.size)
        onProgress(initialProgress)

        var progress = initialProgress
        books.forEach { book ->
          when (val result = updateRemoteBookProgress(book, progressByBookId[book.metadata.id])) {
            is OperationResult.Success -> {
              progress = progress.advance()
              onProgress(progress)
            }

            is OperationResult.Error -> {
              return@withLock OperationResult.Error(result.code, result.message)
            }
          }
        }

        OperationResult.Success(Unit)
      }

    suspend fun uploadPlaybackProgress(
      bookId: String,
      progress: WebdavPlaybackProgress?,
    ): OperationResult<Unit> =
      metadataMutationMutex.withLock {
        val book =
          ensureManageIndexedBooksLocked(forceRefresh = false)[bookId]
            ?.takeIf { it.isAdded }
            ?: return@withLock OperationResult.Error(OperationError.NotFoundError)

        updateRemoteBookProgress(book, progress)
      }

    suspend fun fetchRemotePlaybackProgress(
      onProgress: (WebdavRefreshProgress) -> Unit = {},
    ): OperationResult<List<WebdavRemotePlaybackProgress>> =
      metadataMutationMutex.withLock {
        val books = ensureManageIndexedBooksLocked(forceRefresh = false).values.filter { it.isAdded }
        val initialProgress = WebdavRefreshProgress.start(totalBooks = books.size)
        onProgress(initialProgress)

        var progress = initialProgress
        val items = mutableListOf<WebdavRemotePlaybackProgress>()
        books.forEach { book ->
          when (val result = readFreshPrimaryMetadata(book)) {
            is OperationResult.Success -> {
              val metadata = result.data.metadata
              putIndexedBook(
                book.copy(
                  metadata = metadata,
                  metadataEtag = result.data.eTag,
                  metadataLastModified = result.data.lastModified,
                  metadataPath = result.data.metadataPath,
                ),
              )
              items.add(
                WebdavRemotePlaybackProgress(
                  bookId = metadata.id,
                  title = metadata.title,
                  author = metadata.authorOrNull(),
                  progress = metadata.progress,
                ),
              )
              progress = progress.advance()
              onProgress(progress)
            }

            is OperationResult.Error -> {
              return@withLock OperationResult.Error(result.code, result.message)
            }
          }
        }

        OperationResult.Success(items)
      }

    suspend fun fetchRemotePlaybackProgress(bookId: String): OperationResult<WebdavRemotePlaybackProgress> =
      metadataMutationMutex.withLock {
        val book =
          ensureManageIndexedBooksLocked(forceRefresh = false)[bookId]
            ?.takeIf { it.isAdded }
            ?: return@withLock OperationResult.Error(OperationError.NotFoundError)

        when (val result = readFreshPrimaryMetadata(book)) {
          is OperationResult.Success -> {
            val metadata = result.data.metadata
            putIndexedBook(
              book.copy(
                metadata = metadata,
                metadataEtag = result.data.eTag,
                metadataLastModified = result.data.lastModified,
                metadataPath = result.data.metadataPath,
              ),
            )

            OperationResult.Success(
              WebdavRemotePlaybackProgress(
                bookId = metadata.id,
                title = metadata.title,
                author = metadata.authorOrNull(),
                progress = metadata.progress,
              ),
            )
          }

          is OperationResult.Error -> {
            OperationResult.Error(result.code, result.message)
          }
        }
      }

    override suspend fun fetchLibraries(): OperationResult<List<Library>> =
      OperationResult.Success(
        listOf(
          Library(
            id = WEBDAV_LIBRARY_ID,
            title = WEBDAV_LIBRARY_TITLE,
            type = LibraryType.LIBRARY,
          ),
        ),
      )

    override fun fetchConnectionHost(): OperationResult<ConnectionHost> =
      preferences
        .getHost()
        ?.let { OperationResult.Success(ConnectionHost.external(it)) }
        ?: OperationResult.Error(OperationError.MissingCredentialsHost)

    override suspend fun fetchConnectionInfo(): OperationResult<ConnectionInfo> =
      OperationResult.Success(
        ConnectionInfo(
          username = preferences.getUsername().orEmpty(),
          serverVersion = null,
          buildNumber = null,
        ),
      )

    override suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> =
      OperationResult.Success(
        fallbackRecentPlayback(
          recentBooks = preferences.getRecentBooks(),
          playingItem = preferences.getPlayingItem(),
          snapshot = preferences.getPlayingItem()?.let { preferences.getPlaybackSnapshot(it.id) },
        ),
      )

    override suspend fun fetchBook(
      bookId: String,
      focusChapterId: String?,
    ): OperationResult<DetailedItem> {
      val indexedBook = ensureIndexedBooks()[bookId] ?: return OperationResult.Error(OperationError.NotFoundError)
      val cachedDetail = persistentCache.readBookDetail(bookId)

      if (cachedDetail != null) {
        if (
          shouldUseCachedWebdavDetail(
            cache = cachedDetail,
            directoryEtag = indexedBook.directory.eTag,
            directoryLastModified = indexedBook.directory.lastModified,
          )
        ) {
          return OperationResult.Success(
            cachedDetail.item.copy(
              id = indexedBook.metadata.id,
              title = indexedBook.metadata.title,
              author = indexedBook.metadata.authorOrNull(),
              abstract = indexedBook.metadata.descriptionOrNull(),
              libraryId = WEBDAV_LIBRARY_ID,
              introSkipSeconds = indexedBook.metadata.introSkipSecondsOrDefault(),
              outroSkipSeconds = indexedBook.metadata.outroSkipSecondsOrDefault(),
              createdAt = parseTimestamp(indexedBook.directory.lastModified),
              updatedAt = parseTimestamp(indexedBook.directory.lastModified),
            ),
          )
        }

        persistentCache.removeBookDetail(bookId)
      }

      val filesResult =
        webdavClient
          .listResources(relativePath = indexedBook.directory.relativePath, depth = 1)
          .map { resources -> resources.filter { it.isDirectory.not() }.filter { it.name.isAudioFileName() } }

      return filesResult.foldAsync(
        onSuccess = { resources ->
          val orderedResources = resources.sortedByNaturalName()
          val detail = buildWebdavDetail(indexedBook, orderedResources)
          val timestamp = parseTimestamp(indexedBook.directory.lastModified)
          val item =
            detail.copy(
              id = indexedBook.metadata.id,
              title = indexedBook.metadata.title,
              libraryId = WEBDAV_LIBRARY_ID,
              createdAt = timestamp,
              updatedAt = timestamp,
            )

          persistentCache.saveBookDetail(
            WebdavBookDetailCache(
              bookId = indexedBook.metadata.id,
              directoryEtag = indexedBook.directory.eTag,
              directoryLastModified = indexedBook.directory.lastModified,
              item = item,
            ),
          )

          OperationResult.Success(item)
        },
        onFailure = { OperationResult.Error(it.code) },
      )
    }

    override suspend fun fetchBookmarks(libraryItemId: String): OperationResult<List<Bookmark>> =
      OperationResult.Error(OperationError.UnsupportedError)

    override suspend fun dropBookmark(bookmark: Bookmark): OperationResult<Unit> = OperationResult.Error(OperationError.UnsupportedError)

    override suspend fun createBookmark(request: CreateBookmarkRequest): OperationResult<Bookmark> =
      OperationResult.Error(OperationError.UnsupportedError)

    override suspend fun refreshRemoteCache(): OperationResult<Unit> = refreshRemoteCache(onProgress = {})

    suspend fun refreshRemoteCache(onProgress: (WebdavRefreshProgress) -> Unit = {}): OperationResult<Unit> =
      metadataMutationMutex.withLock {
        refreshRemoteCacheLocked(onProgress)
      }

    private suspend fun refreshRemoteCacheLocked(onProgress: (WebdavRefreshProgress) -> Unit): OperationResult<Unit> {
      val persistedByDirectory = persistentCache.readBookIndex().associateBy { it.directoryPath }
      val resourcesResult =
        webdavClient.listResources(
          relativePath = "",
          depth = 1,
        )

      return resourcesResult.foldAsync(
        onSuccess = { resources ->
          val directories = resources.filter { it.isDirectory }
          val initialProgress = WebdavRefreshProgress.start(totalBooks = directories.size)
          onProgress(initialProgress)

          val progressMutex = Mutex()
          var progress = initialProgress
          val semaphore = Semaphore(REFRESH_PARALLELISM)

          val refreshed =
            coroutineScope {
              directories
                .map { directory ->
                  async {
                    semaphore.withPermit {
                      val persisted = persistedByDirectory[directory.relativePath]
                      val metadataResolution =
                        ensureBookMetadata(directory = directory, previous = persisted)
                          ?: return@withPermit null

                      val coverValidation =
                        resolveCoverValidation(
                          directory = directory,
                          coverName = metadataResolution.metadata.coverOrDefault(),
                          previous = persisted,
                          force = true,
                          bookId = metadataResolution.metadata.id,
                          dropLocalCoverOnChange = false,
                        )

                      val book =
                        IndexedBook(
                          metadata = metadataResolution.metadata,
                          directory = directory,
                          metadataEtag = metadataResolution.eTag,
                          metadataLastModified = metadataResolution.lastModified,
                          metadataPath = metadataResolution.metadataPath,
                          coverEtag = coverValidation.eTag,
                          coverLastModified = coverValidation.lastModified,
                          resolvedCoverName = null,
                          isCoverMissing = false,
                          isAdded = persisted?.isAdded ?: true,
                        )

                      progressMutex.withLock {
                        progress = progress.advance()
                        onProgress(progress)
                      }

                      book
                    }
                  }
                }.awaitAll()
                .filterNotNull()
            }

          if (refreshed.size != directories.size) {
            return@foldAsync OperationResult.Error(OperationError.InternalError)
          }

          val mapped = refreshed.associateBy { it.metadata.id }

          cachedBooks = mapped
          clearCachedCoverFiles()
          persistentCache.clearBookDetails()
          persistentCache.saveBookIndex(mapped.values.map { it.toIndexEntry() })
          OperationResult.Success(Unit)
        },
        onFailure = { OperationResult.Error(it.code, it.message) },
      )
    }

    override suspend fun refreshItemCache(itemId: String): OperationResult<Unit> =
      metadataMutationMutex.withLock {
        refreshItemCacheLocked(itemId)
      }

    private suspend fun refreshItemCacheLocked(itemId: String): OperationResult<Unit> {
      val current = ensureIndexedBooks()[itemId] ?: return OperationResult.Error(OperationError.NotFoundError)
      val resourcesResult =
        webdavClient.listResources(
          relativePath = "",
          depth = 1,
        )

      return resourcesResult.foldAsync(
        onSuccess = { resources ->
          val directory =
            resources
              .filter { it.isDirectory }
              .firstOrNull { it.relativePath == current.directory.relativePath }
              ?: return@foldAsync OperationResult.Error(OperationError.NotFoundError)

          val metadataResolution =
            ensureBookMetadata(
              directory = directory,
              previous = current.toIndexEntry(),
            ) ?: return@foldAsync OperationResult.Error(OperationError.InternalError)

          putIndexedBook(
            current.copy(
              metadata = metadataResolution.metadata,
              directory = directory,
              metadataEtag = metadataResolution.eTag,
              metadataLastModified = metadataResolution.lastModified,
              metadataPath = metadataResolution.metadataPath,
              coverEtag = null,
              coverLastModified = null,
              resolvedCoverName = null,
              isCoverMissing = false,
              isAdded = current.isAdded,
            ),
          )
          persistentCache.removeBookDetail(itemId)
          dropLocalCover(itemId)

          OperationResult.Success(Unit)
        },
        onFailure = { OperationResult.Error(it.code) },
      )
    }

    private suspend fun ensureIndexedBooks(): Map<String, IndexedBook> {
      val inMemory = cachedBooks
      if (inMemory.isNotEmpty()) {
        return inMemory
      }
      val persisted = loadPersistedIndex()
      return when (
        resolveWebdavIndexSource(
          hasInMemoryIndex = false,
          hasPersistedIndex = persisted.isNotEmpty(),
        )
      ) {
        WebdavIndexSource.MEMORY -> cachedBooks
        WebdavIndexSource.PERSISTED -> persisted
        WebdavIndexSource.EMPTY -> rebuildIndex(force = true)
      }
    }

    private suspend fun ensureManageIndexedBooks(forceRefresh: Boolean): Map<String, IndexedBook> {
      if (forceRefresh) {
        return rebuildIndex(force = true)
      }

      if (cachedBooks.isNotEmpty()) {
        return cachedBooks
      }

      val persisted = loadPersistedIndex()
      if (persisted.isNotEmpty()) {
        return persisted
      }

      return rebuildIndex(force = true)
    }

    private suspend fun ensureManageIndexedBooksLocked(forceRefresh: Boolean): Map<String, IndexedBook> {
      if (forceRefresh) {
        return rebuildIndexLocked(force = true)
      }

      if (cachedBooks.isNotEmpty()) {
        return cachedBooks
      }

      val persisted = loadPersistedIndex()
      if (persisted.isNotEmpty()) {
        return persisted
      }

      return rebuildIndexLocked(force = true)
    }

    private suspend fun loadPersistedIndex(): Map<String, IndexedBook> =
      persistentCache
        .readBookIndex()
        .associateBy { it.bookId }
        .mapValues { (_, value) -> value.toIndexedBook() }
        .also { cachedBooks = it }

    private fun Map<String, IndexedBook>.toManageItems(): List<WebdavManageBookItem> =
      values
        .sortedWith(indexedBookComparator(preferences.getLibraryOrdering()))
        .map {
          WebdavManageBookItem(
            id = it.metadata.id,
            title = it.metadata.title,
            isAdded = it.isAdded,
          )
        }

    private fun indexedBookComparator(ordering: LibraryOrderingConfiguration): Comparator<IndexedBook> {
      val primaryComparator =
        when (ordering.option) {
          LibraryOrderingOption.TITLE -> {
            compareBy<IndexedBook> { it.metadata.title.lowercase() }
          }

          LibraryOrderingOption.AUTHOR -> {
            compareBy<IndexedBook> {
              it
                .metadata
                .authorOrNull()
                ?.lowercase()
                .orEmpty()
            }.thenBy {
              it
                .metadata
                .title
                .lowercase()
            }
          }

          LibraryOrderingOption.CREATED_AT -> {
            compareBy<IndexedBook> { parseTimestamp(it.directory.lastModified) }
              .thenBy { it.metadata.title.lowercase() }
          }

          LibraryOrderingOption.UPDATED_AT -> {
            compareBy<IndexedBook> { parseTimestamp(it.directory.lastModified) }
              .thenBy { it.metadata.title.lowercase() }
          }
        }

      return when (ordering.direction) {
        LibraryOrderingDirection.ASCENDING -> primaryComparator
        LibraryOrderingDirection.DESCENDING -> primaryComparator.reversed()
      }
    }

    private suspend fun rebuildIndex(force: Boolean): Map<String, IndexedBook> =
      metadataMutationMutex.withLock {
        rebuildIndexLocked(force)
      }

    private suspend fun rebuildIndexLocked(force: Boolean): Map<String, IndexedBook> =
      rebuildIndexStrictLocked(force).foldAsync(
        onSuccess = { it },
        onFailure = { error ->
          Timber.w("Unable to list WebDAV directories for index: ${error.code}")
          loadPersistedIndex()
        },
      )

    private suspend fun rebuildIndexStrict(force: Boolean): OperationResult<Map<String, IndexedBook>> =
      metadataMutationMutex.withLock {
        rebuildIndexStrictLocked(force)
      }

    private suspend fun rebuildIndexStrictLocked(force: Boolean): OperationResult<Map<String, IndexedBook>> {
      val persistedIndex = persistentCache.readBookIndex()
      val persistedByDirectory = persistedIndex.associateBy { it.directoryPath }
      val resourcesResult =
        webdavClient.listResources(
          relativePath = "",
          depth = 1,
        )

      return resourcesResult.foldAsync(
        onSuccess = { resources ->
          val directories = resources.filter { it.isDirectory }
          val semaphore = Semaphore(REFRESH_PARALLELISM)

          val entries =
            coroutineScope {
              directories
                .map { directory ->
                  async {
                    semaphore.withPermit {
                      val persisted = persistedByDirectory[directory.relativePath]
                      val metadataResolution = ensureBookMetadata(directory, persisted) ?: return@withPermit null

                      val coverValidation =
                        resolveCoverValidation(
                          directory = directory,
                          coverName = metadataResolution.metadata.coverOrDefault(),
                          previous = persisted,
                          force = force,
                          bookId = metadataResolution.metadata.id,
                        )

                      val directoryChanged = isDirectoryValidationChanged(persisted, directory)

                      val indexedBook =
                        IndexedBook(
                          metadata = metadataResolution.metadata,
                          directory = directory,
                          metadataEtag = metadataResolution.eTag,
                          metadataLastModified = metadataResolution.lastModified,
                          metadataPath = metadataResolution.metadataPath,
                          coverEtag = coverValidation.eTag,
                          coverLastModified = coverValidation.lastModified,
                          resolvedCoverName = if (force) null else persisted?.resolvedCoverName,
                          isCoverMissing = if (force) false else persisted?.isCoverMissing ?: false,
                          isAdded = persisted?.isAdded ?: true,
                        )

                      RebuildEntry(book = indexedBook, directoryChanged = directoryChanged)
                    }
                  }
                }.awaitAll()
                .filterNotNull()
            }

          entries
            .filter { it.directoryChanged }
            .forEach { persistentCache.removeBookDetail(it.book.metadata.id) }

          val mapped = entries.associate { it.book.metadata.id to it.book }

          persistentCache.saveBookIndex(
            mapped
              .values
              .map { it.toIndexEntry() },
          )

          cachedBooks = mapped
          OperationResult.Success(mapped)
        },
        onFailure = { error ->
          OperationResult.Error(error.code, error.message)
        },
      )
    }

    private suspend fun ensureBookMetadata(
      directory: WebdavResource,
      previous: WebdavBookIndexEntry?,
    ): MetadataResolution? =
      ensureBookMetadataFromCandidates(
        directory = directory,
        previous = previous,
        candidatePaths = metadataFilePathCandidates(directory.relativePath),
      )

    private suspend fun ensureBookMetadataFromCandidates(
      directory: WebdavResource,
      previous: WebdavBookIndexEntry?,
      candidatePaths: List<String>,
    ): MetadataResolution? {
      val metadataPath = candidatePaths.firstOrNull()
      if (metadataPath == null) {
        val created = createDefaultMetadata(directory)
        return MetadataResolution(
          metadata = created,
          eTag = null,
          lastModified = null,
          metadataPath = "${directory.relativePath}/$BOOK_METADATA_FILE_NAME",
          exists = true,
        )
      }

      return resolveMetadataConditionally(
        conditionalResult = readMetadataConditionally(metadataPath, previousForMetadataPath(previous, metadataPath)),
        previous = previous,
        metadataPath = metadataPath,
        onMissing = {
          ensureBookMetadataFromCandidates(
            directory = directory,
            previous = previous,
            candidatePaths = candidatePaths.drop(1),
          )
        },
      )
    }

    private suspend fun readMetadataConditionally(
      relativePath: String,
      previous: WebdavBookIndexEntry?,
    ): OperationResult<ConditionalTextResponse> =
      webdavClient.readTextConditionally(
        relativePath = relativePath,
        knownEtag = previous?.metadataEtag,
        knownLastModified = previous?.metadataLastModified,
      )

    private suspend fun resolveMetadataConditionally(
      conditionalResult: OperationResult<ConditionalTextResponse>,
      previous: WebdavBookIndexEntry?,
      metadataPath: String,
      onMissing: suspend () -> MetadataResolution?,
    ): MetadataResolution? =
      conditionalResult.foldAsync(
        onSuccess = { conditional ->
          when (conditional.status) {
            ConditionalFetchStatus.NOT_MODIFIED -> {
              previous
                ?.toMetadata()
                ?.let {
                  MetadataResolution(
                    metadata = it,
                    eTag = previous.metadataEtag ?: conditional.eTag,
                    lastModified = previous.metadataLastModified ?: conditional.lastModified,
                    metadataPath = previous.metadataPath ?: metadataPath,
                    exists = true,
                  )
                }
            }

            ConditionalFetchStatus.UPDATED -> {
              val metadata = conditional.content?.let { parseMetadataOrNull(it) } ?: previous?.toMetadata()
              metadata?.let {
                MetadataResolution(
                  metadata = it,
                  eTag = conditional.eTag,
                  lastModified = conditional.lastModified,
                  metadataPath = metadataPath,
                  exists = true,
                )
              }
            }

            ConditionalFetchStatus.NOT_FOUND -> {
              onMissing()
            }
          }
        },
        onFailure = {
          previous
            ?.toMetadata()
            ?.let {
              MetadataResolution(
                metadata = it,
                eTag = previous.metadataEtag,
                lastModified = previous.metadataLastModified,
                metadataPath = previous.metadataPath ?: metadataPath,
                exists = true,
              )
            }
        },
      )

    private fun previousForMetadataPath(
      previous: WebdavBookIndexEntry?,
      metadataPath: String,
    ): WebdavBookIndexEntry? = previous?.takeIf { it.metadataPath == metadataPath }

    private suspend fun resolveCoverValidation(
      directory: WebdavResource,
      coverName: String,
      previous: WebdavBookIndexEntry?,
      force: Boolean,
      bookId: String,
      dropLocalCoverOnChange: Boolean = true,
    ): CoverValidation {
      if (!force) {
        return CoverValidation(
          eTag = previous?.coverEtag,
          lastModified = previous?.coverLastModified,
        )
      }

      val coverPath = "${directory.relativePath}/$coverName"

      return webdavClient
        .head(coverPath)
        .fold(
          onSuccess = { headers ->
            val newEtag = findHeaderIgnoreCase(headers, "ETag")
            val newLastModified = findHeaderIgnoreCase(headers, "Last-Modified")

            if (
              dropLocalCoverOnChange &&
              isValidationChanged(previous?.coverEtag, previous?.coverLastModified, newEtag, newLastModified)
            ) {
              dropLocalCover(bookId)
            }

            CoverValidation(
              eTag = newEtag,
              lastModified = newLastModified,
            )
          },
          onFailure = {
            when (it.code) {
              OperationError.NotFoundError -> {
                if (dropLocalCoverOnChange) {
                  dropLocalCover(bookId)
                }
                CoverValidation(null, null)
              }

              else -> {
                CoverValidation(
                  eTag = previous?.coverEtag,
                  lastModified = previous?.coverLastModified,
                )
              }
            }
          },
        )
    }

    private fun buildWebdavDetail(
      indexedBook: IndexedBook,
      orderedResources: List<WebdavResource>,
    ): DetailedItem {
      val files = mutableListOf<BookFile>()
      val chapters = mutableListOf<PlayingChapter>()

      var chapterStart = 0.0

      orderedResources.forEach { resource ->
        val chapterId = WebdavPathCodec.encode(resource.relativePath)
        val displayName = buildTrackDisplayTitle(resource.name)
        val chapterTitle = displayName
        val timelineDuration = unresolvedTimelineDurationSeconds

        files.add(
          BookFile(
            id = chapterId,
            name = displayName,
            duration = unresolvedDisplayDurationSeconds,
            mimeType = resource.mimeType ?: guessMimeType(resource.name),
            size = resource.size,
          ),
        )

        chapters.add(
          PlayingChapter(
            available = true,
            duration = unresolvedDisplayDurationSeconds,
            start = chapterStart,
            end = chapterStart + timelineDuration,
            title = chapterTitle,
            id = chapterId,
          ),
        )

        chapterStart += timelineDuration
      }

      return DetailedItem(
        id = indexedBook.metadata.id,
        title = indexedBook.metadata.title,
        subtitle = null,
        author = indexedBook.metadata.authorOrNull(),
        narrator = null,
        publisher = null,
        series = emptyList(),
        year = null,
        abstract = indexedBook.metadata.descriptionOrNull(),
        files = files,
        chapters = chapters,
        progress = null,
        libraryId = WEBDAV_LIBRARY_ID,
        introSkipSeconds = indexedBook.metadata.introSkipSecondsOrDefault(),
        outroSkipSeconds = indexedBook.metadata.outroSkipSecondsOrDefault(),
        localProvided = false,
        createdAt = 0L,
        updatedAt = 0L,
      )
    }

    private suspend fun createDefaultMetadata(directory: WebdavResource): WebdavBookMetadata {
      val defaultMetadata =
        WebdavBookMetadata(
          version = 1,
          id = defaultWebdavMetadataId(directory.relativePath),
          title = directory.name,
          author = null,
          description = null,
          cover = "cover.jpg",
        )

      val metadataPath = "${directory.relativePath}/$BOOK_METADATA_FILE_NAME"
      when (val result = webdavClient.putTextIfAbsent(metadataPath, metadataAdapter.toJson(defaultMetadata))) {
        is OperationResult.Error -> {
          Timber.w("Unable to persist default WebDAV metadata for %s: %s", directory.relativePath, result.code)
        }

        is OperationResult.Success -> {
          Unit
        }
      }

      return defaultMetadata
    }

    private fun parseMetadataOrNull(content: String): WebdavBookMetadata? =
      runCatching { metadataAdapter.fromJson(content) }
        .getOrNull()
        ?.takeIf { it.id.isNotBlank() && it.title.isNotBlank() }

    private fun isDirectoryValidationChanged(
      previous: WebdavBookIndexEntry?,
      currentDirectory: WebdavResource,
    ): Boolean {
      if (previous == null) {
        return false
      }

      return isValidationChanged(
        previousEtag = previous.directoryEtag,
        previousLastModified = previous.directoryLastModified,
        currentEtag = currentDirectory.eTag,
        currentLastModified = currentDirectory.lastModified,
      )
    }

    private fun isValidationChanged(
      previousEtag: String?,
      previousLastModified: String?,
      currentEtag: String?,
      currentLastModified: String?,
    ): Boolean = !isValidationSame(previousEtag, previousLastModified, currentEtag, currentLastModified)

    private fun isValidationSame(
      previousEtag: String?,
      previousLastModified: String?,
      currentEtag: String?,
      currentLastModified: String?,
    ): Boolean =
      when {
        !previousEtag.isNullOrBlank() && !currentEtag.isNullOrBlank() -> {
          previousEtag == currentEtag
        }

        !previousLastModified.isNullOrBlank() && !currentLastModified.isNullOrBlank() -> {
          previousLastModified == currentLastModified
        }

        else -> {
          previousEtag.isNullOrBlank() &&
            previousLastModified.isNullOrBlank() &&
            currentEtag.isNullOrBlank() &&
            currentLastModified.isNullOrBlank()
        }
      }

    private fun findHeaderIgnoreCase(
      headers: Map<String, String>,
      name: String,
    ): String? =
      headers
        .entries
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.value

    private fun WebdavBookIndexEntry.toMetadata(): WebdavBookMetadata =
      WebdavBookMetadata(
        version = 1,
        id = bookId,
        title = title,
        author = author,
        description = description,
        cover = coverName,
        introSkipSeconds = introSkipSeconds,
        outroSkipSeconds = outroSkipSeconds,
        progress = progress,
      )

    private fun WebdavBookIndexEntry.toIndexedBook(): IndexedBook =
      IndexedBook(
        metadata = toMetadata(),
        directory =
          WebdavResource(
            relativePath = directoryPath,
            name = directoryPath.substringAfterLast('/'),
            isDirectory = true,
            eTag = directoryEtag,
            lastModified = directoryLastModified,
            size = null,
            mimeType = null,
          ),
        metadataEtag = metadataEtag,
        metadataLastModified = metadataLastModified,
        metadataPath = metadataPath,
        coverEtag = coverEtag,
        coverLastModified = coverLastModified,
        resolvedCoverName = resolvedCoverName,
        isCoverMissing = isCoverMissing,
        isAdded = isAdded,
      )

    private fun IndexedBook.toIndexEntry(): WebdavBookIndexEntry =
      WebdavBookIndexEntry(
        bookId = metadata.id,
        directoryPath = directory.relativePath,
        directoryEtag = directory.eTag,
        directoryLastModified = directory.lastModified,
        title = metadata.title,
        author = metadata.authorOrNull(),
        description = metadata.descriptionOrNull(),
        coverName = metadata.coverOrDefault(),
        metadataEtag = metadataEtag,
        metadataLastModified = metadataLastModified,
        metadataPath = metadataPath,
        coverEtag = coverEtag,
        coverLastModified = coverLastModified,
        introSkipSeconds = metadata.introSkipSecondsOrDefault(),
        outroSkipSeconds = metadata.outroSkipSecondsOrDefault(),
        progress = metadata.progress,
        resolvedCoverName = resolvedCoverName,
        isCoverMissing = isCoverMissing,
        isAdded = isAdded,
      )

    private fun IndexedBook.updateCoverState(transform: (WebdavBookIndexEntry) -> WebdavBookIndexEntry): IndexedBook =
      transform(toIndexEntry()).toIndexedBook()

    private suspend fun updateIndexedBook(
      bookId: String,
      transform: (WebdavBookIndexEntry) -> WebdavBookIndexEntry,
    ) {
      val current = cachedBooks[bookId] ?: return
      putIndexedBook(current.updateCoverState(transform))
    }

    private suspend fun putIndexedBook(updated: IndexedBook) {
      cachedBooks =
        cachedBooks
          .toMutableMap()
          .apply { put(updated.metadata.id, updated) }
      addedBooksSortedCache = null
      persistentCache.saveBookIndex(cachedBooks.values.map { it.toIndexEntry() })
    }

    private suspend fun updateRemoteBookProgress(
      book: IndexedBook,
      progress: WebdavPlaybackProgress?,
    ): OperationResult<Unit> {
      val metadataResult = readFreshPrimaryMetadata(book)
      if (metadataResult is OperationResult.Error) {
        return OperationResult.Error(metadataResult.code, metadataResult.message)
      }

      val metadataResolution = (metadataResult as OperationResult.Success).data
      val metadataPath = "${book.directory.relativePath}/$BOOK_METADATA_FILE_NAME"
      val updatedMetadata = metadataResolution.metadata.copy(progress = progress)
      val content = metadataAdapter.toJson(updatedMetadata)
      val writeResult =
        when (metadataResolution.exists) {
          true -> {
            val hasOverwriteCondition =
              !metadataResolution.eTag.isNullOrBlank() ||
                !metadataResolution.lastModified.isNullOrBlank()

            when (hasOverwriteCondition) {
              true -> {
                webdavClient.putText(
                  relativePath = metadataPath,
                  content = content,
                  knownEtag = metadataResolution.eTag,
                  knownLastModified = metadataResolution.lastModified,
                )
              }

              false -> {
                webdavClient.putTextOverwrite(
                  relativePath = metadataPath,
                  content = content,
                )
              }
            }
          }

          false -> {
            webdavClient.putTextOverwrite(
              relativePath = metadataPath,
              content = content,
            )
          }
        }

      return writeResult.foldAsync(
        onSuccess = { response ->
          val updatedBook =
            book.copy(
              metadata = updatedMetadata,
              metadataEtag = response.eTag,
              metadataLastModified = response.lastModified,
              metadataPath = metadataPath,
            )
          putIndexedBook(updatedBook)
          persistentCache.removeBookDetail(book.metadata.id)
          OperationResult.Success(Unit)
        },
        onFailure = { OperationResult.Error(it.code, it.message) },
      )
    }

    private suspend fun readFreshPrimaryMetadata(book: IndexedBook): OperationResult<MetadataResolution> {
      val metadataPath = "${book.directory.relativePath}/$BOOK_METADATA_FILE_NAME"
      val previous = book.toIndexEntry().takeIf { book.metadataPath == metadataPath }

      return readMetadataConditionally(metadataPath, previous).foldAsync(
        onSuccess = { conditional ->
          when (conditional.status) {
            ConditionalFetchStatus.NOT_MODIFIED -> {
              OperationResult.Success(
                MetadataResolution(
                  metadata = book.metadata,
                  eTag = conditional.eTag ?: book.metadataEtag,
                  lastModified = conditional.lastModified ?: book.metadataLastModified,
                  metadataPath = metadataPath,
                  exists = true,
                ),
              )
            }

            ConditionalFetchStatus.UPDATED -> {
              val metadata =
                conditional.content
                  ?.let { parseMetadataOrNull(it) }
                  ?: return@foldAsync OperationResult.Error(OperationError.InternalError)

              OperationResult.Success(
                MetadataResolution(
                  metadata = metadata,
                  eTag = conditional.eTag,
                  lastModified = conditional.lastModified,
                  metadataPath = metadataPath,
                  exists = true,
                ),
              )
            }

            ConditionalFetchStatus.NOT_FOUND -> {
              OperationResult.Success(
                MetadataResolution(
                  metadata =
                    WebdavBookMetadata(
                      version = 1,
                      id = book.metadata.id,
                      title = book.metadata.title,
                      author = book.metadata.authorOrNull(),
                      description = book.metadata.descriptionOrNull(),
                      cover = book.metadata.coverOrDefault(),
                      introSkipSeconds = book.metadata.introSkipSecondsOrDefault(),
                      outroSkipSeconds = book.metadata.outroSkipSecondsOrDefault(),
                      progress = book.metadata.progress,
                    ),
                  eTag = null,
                  lastModified = null,
                  metadataPath = metadataPath,
                  exists = false,
                ),
              )
            }
          }
        },
        onFailure = { OperationResult.Error(it.code, it.message) },
      )
    }

    private suspend fun clearCachedCoverFiles() {
      (cachedBooks.keys + persistentCache.readBookIndex().map { it.bookId })
        .toSet()
        .forEach(::dropLocalCover)
    }

    suspend fun clearSessionState() {
      metadataMutationMutex.withLock {
        clearCachedCoverFiles()
        cachedBooks = emptyMap()
        addedBooksSortedCache = null
        persistentCache.clearAll()
      }
    }

    private fun dropLocalCover(bookId: String) {
      runCatching {
        val file = shortTermCacheStorageProperties.provideCoverPath(bookId)
        if (file.exists()) {
          file.delete()
        }
      }.onFailure { Timber.w(it, "Unable to drop local cover for $bookId") }
    }

    private fun parseTimestamp(lastModified: String?): Long =
      runCatching {
        lastModified
          ?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
      }.getOrNull() ?: System.currentTimeMillis()

    private fun guessMimeType(fileName: String): String =
      when (fileName.substringAfterLast('.', "").lowercase()) {
        "m4a", "m4b", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav", "wave" -> "audio/wav"
        else -> "audio/mpeg"
      }

    private fun String.isAudioFileName(): Boolean =
      lowercase().endsWith(".mp3") ||
        lowercase().endsWith(".m4a") ||
        lowercase().endsWith(".m4b") ||
        lowercase().endsWith(".aac") ||
        lowercase().endsWith(".flac") ||
        lowercase().endsWith(".ogg") ||
        lowercase().endsWith(".opus") ||
        lowercase().endsWith(".wav") ||
        lowercase().endsWith(".mp4")

    private fun List<WebdavResource>.sortedByNaturalName(): List<WebdavResource> {
      if (size < 2) return this
      val keyed = map { it to naturalKeyOf(it.name.lowercase()) }
      return keyed
        .sortedWith(naturalKeyComparator)
        .map { it.first }
    }

    private fun naturalKeyOf(name: String): NaturalKey {
      val chunks =
        chunkRegex
          .findAll(name)
          .map { match ->
            val value = match.value
            val number = value.toLongOrNull()
            if (number != null) NaturalChunk(text = null, number = number) else NaturalChunk(text = value, number = null)
          }.toList()
      return NaturalKey(chunks = chunks, original = name)
    }

    data class IndexedBook(
      val metadata: WebdavBookMetadata,
      val directory: WebdavResource,
      val metadataEtag: String?,
      val metadataLastModified: String?,
      val metadataPath: String?,
      val coverEtag: String?,
      val coverLastModified: String?,
      val resolvedCoverName: String?,
      val isCoverMissing: Boolean,
      val isAdded: Boolean,
    )

    data class MetadataResolution(
      val metadata: WebdavBookMetadata,
      val eTag: String?,
      val lastModified: String?,
      val metadataPath: String,
      val exists: Boolean,
    )

    data class CoverValidation(
      val eTag: String?,
      val lastModified: String?,
    )

    private data class RebuildEntry(
      val book: IndexedBook,
      val directoryChanged: Boolean,
    )

    private data class NaturalChunk(
      val text: String?,
      val number: Long?,
    )

    private data class NaturalKey(
      val chunks: List<NaturalChunk>,
      val original: String,
    )

    companion object {
      const val WEBDAV_LIBRARY_ID = "webdav_library"
      const val WEBDAV_LIBRARY_TITLE = "媒体库"
      internal const val BOOK_METADATA_FILE_NAME = ".lissen-book.json"
      internal const val LEGACY_BOOK_METADATA_FILE_NAME = ".webdav-player-book.json"

      private const val REFRESH_PARALLELISM = 6

      private val chunkRegex = Regex("""\d+|\D+""")
      private const val unresolvedDisplayDurationSeconds = 0.0
      private const val unresolvedTimelineDurationSeconds = 1.0

      internal fun metadataFilePathCandidates(directoryRelativePath: String): List<String> =
        listOf(BOOK_METADATA_FILE_NAME, LEGACY_BOOK_METADATA_FILE_NAME)
          .map { "$directoryRelativePath/$it" }

      internal fun defaultWebdavMetadataId(directoryRelativePath: String): String =
        UUID.nameUUIDFromBytes(directoryRelativePath.toByteArray(StandardCharsets.UTF_8)).toString()

      private val naturalKeyComparator =
        Comparator<Pair<WebdavResource, NaturalKey>> { left, right ->
          val first = left.second
          val second = right.second
          val maxChunks = maxOf(first.chunks.size, second.chunks.size)
          for (index in 0 until maxChunks) {
            val firstChunk = first.chunks.getOrNull(index) ?: return@Comparator -1
            val secondChunk = second.chunks.getOrNull(index) ?: return@Comparator 1
            val comparison =
              when {
                firstChunk.number != null && secondChunk.number != null -> {
                  firstChunk.number.compareTo(secondChunk.number)
                }

                else -> {
                  (firstChunk.text ?: firstChunk.number.toString())
                    .compareTo(secondChunk.text ?: secondChunk.number.toString())
                }
              }
            if (comparison != 0) {
              return@Comparator comparison
            }
          }
          first.original.compareTo(second.original)
        }
    }
  }
