package org.grakovne.lissen.channel.webdav

import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.channel.common.ConnectionHost
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.common.RefreshableChannel
import org.grakovne.lissen.channel.webdav.cache.WebdavBookDetailCache
import org.grakovne.lissen.channel.webdav.cache.WebdavBookIndexEntry
import org.grakovne.lissen.channel.webdav.cache.WebdavPersistentCache
import org.grakovne.lissen.channel.webdav.client.ConditionalFetchStatus
import org.grakovne.lissen.channel.webdav.client.WebdavClient
import org.grakovne.lissen.channel.webdav.model.WebdavBookMetadata
import org.grakovne.lissen.channel.webdav.model.WebdavResource
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.content.cache.temporary.ShortTermCacheStorageProperties
import org.grakovne.lissen.content.fallbackRecentPlayback
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.Bookmark
import org.grakovne.lissen.lib.domain.CreateBookmarkRequest
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavMediaChannel
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val webdavClient: WebdavClient,
    private val persistentCache: WebdavPersistentCache,
    private val shortTermCacheStorageProperties: ShortTermCacheStorageProperties,
  ) : MediaChannel,
    RefreshableChannel {
    private val metadataAdapter = moshi.adapter(WebdavBookMetadata::class.java)
    private val metadataMutationMutex = Mutex()

    private var cachedBooks: Map<String, IndexedBook> = emptyMap()

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
      val allBooks =
        filterAddedBooks(ensureIndexedBooks().values.map { it.toIndexEntry() })
          .sortedBy { it.title.lowercase() }
          .map {
            Book(
              id = it.bookId,
              subtitle = null,
              series = null,
              title = it.title,
              author = it.author,
            )
          }

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
        filterAddedBooks(ensureIndexedBooks().values.map { it.toIndexEntry() })
          .map {
            Book(
              id = it.bookId,
              subtitle = null,
              series = null,
              title = it.title,
              author = it.author,
            )
          }.filter { book ->
            book.title.lowercase().contains(lowered) ||
              (book.author?.lowercase()?.contains(lowered) == true)
          }.take(limit)

      return OperationResult.Success(result)
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

      cachedDetail
        ?.let {
          return OperationResult.Success(
            it.item.copy(
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

      val filesResult =
        webdavClient
          .listResources(relativePath = indexedBook.directory.relativePath, depth = 1)
          .map { resources -> resources.filter { it.isDirectory.not() }.filter { it.name.isAudioFileName() } }

      return filesResult.foldAsync(
        onSuccess = { resources ->
          val orderedResources = resources.sortedWith(compareByNaturalResourceName())
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
          var progress = WebdavRefreshProgress.start(totalBooks = directories.size)
          onProgress(progress)

          val mapped = mutableMapOf<String, IndexedBook>()

          directories.forEach { directory ->
            val persisted = persistedByDirectory[directory.relativePath]
            val metadataResolution =
              ensureBookMetadata(
                directory = directory,
                previous = persisted,
              ) ?: return@foldAsync OperationResult.Error(OperationError.InternalError)

            val coverValidation =
              resolveCoverValidation(
                directory = directory,
                coverName = metadataResolution.metadata.coverOrDefault(),
                previous = persisted,
                force = true,
                bookId = metadataResolution.metadata.id,
                dropLocalCoverOnChange = false,
              )

            mapped[metadataResolution.metadata.id] =
              IndexedBook(
                metadata = metadataResolution.metadata,
                directory = directory,
                metadataEtag = metadataResolution.eTag,
                metadataLastModified = metadataResolution.lastModified,
                coverEtag = coverValidation.eTag,
                coverLastModified = coverValidation.lastModified,
                resolvedCoverName = null,
                isCoverMissing = false,
                isAdded = persisted?.isAdded ?: false,
              )

            progress = progress.advance()
            onProgress(progress)
          }

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
      val persisted = loadPersistedIndex()

      return when (
        resolveWebdavIndexSource(
          hasInMemoryIndex = cachedBooks.isNotEmpty(),
          hasPersistedIndex = persisted.isNotEmpty(),
        )
      ) {
        WebdavIndexSource.MEMORY -> {
          cachedBooks
        }

        WebdavIndexSource.PERSISTED -> {
          persisted
        }

        WebdavIndexSource.EMPTY -> {
          emptyMap()
        }
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

    private fun loadPersistedIndex(): Map<String, IndexedBook> =
      persistentCache
        .readBookIndex()
        .associateBy { it.bookId }
        .mapValues { (_, value) -> value.toIndexedBook() }
        .also { cachedBooks = it }

    private fun Map<String, IndexedBook>.toManageItems(): List<WebdavManageBookItem> =
      values
        .sortedBy { it.metadata.title.lowercase() }
        .map {
          WebdavManageBookItem(
            id = it.metadata.id,
            title = it.metadata.title,
            isAdded = it.isAdded,
          )
        }

    private suspend fun rebuildIndex(force: Boolean): Map<String, IndexedBook> =
      metadataMutationMutex.withLock {
        rebuildIndexLocked(force)
      }

    private suspend fun rebuildIndexLocked(force: Boolean): Map<String, IndexedBook> =
      rebuildIndexStrictLocked(force).fold(
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
          val mapped = mutableMapOf<String, IndexedBook>()

          resources
            .filter { it.isDirectory }
            .forEach { directory ->
              val persisted = persistedByDirectory[directory.relativePath]
              val metadataResolution = ensureBookMetadata(directory, persisted) ?: return@forEach

              val coverValidation =
                resolveCoverValidation(
                  directory = directory,
                  coverName = metadataResolution.metadata.coverOrDefault(),
                  previous = persisted,
                  force = force,
                  bookId = metadataResolution.metadata.id,
                )

              if (isDirectoryValidationChanged(persisted, directory)) {
                persistentCache.removeBookDetail(metadataResolution.metadata.id)
              }

              val indexedBook =
                IndexedBook(
                  metadata = metadataResolution.metadata,
                  directory = directory,
                  metadataEtag = metadataResolution.eTag,
                  metadataLastModified = metadataResolution.lastModified,
                  coverEtag = coverValidation.eTag,
                  coverLastModified = coverValidation.lastModified,
                  resolvedCoverName = if (force) null else persisted?.resolvedCoverName,
                  isCoverMissing = if (force) false else persisted?.isCoverMissing ?: false,
                  isAdded = persisted?.isAdded ?: false,
                )

              mapped[metadataResolution.metadata.id] = indexedBook
            }

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
    ): MetadataResolution? {
      val metadataPath = "${directory.relativePath}/.lissen-book.json"
      val conditionalResult =
        webdavClient.readTextConditionally(
          relativePath = metadataPath,
          knownEtag = previous?.metadataEtag,
          knownLastModified = previous?.metadataLastModified,
        )

      return conditionalResult.foldAsync(
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
                )
              }
            }

            ConditionalFetchStatus.NOT_FOUND -> {
              val created = createDefaultMetadata(directory)
              MetadataResolution(
                metadata = created,
                eTag = null,
                lastModified = null,
              )
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
              )
            }
        },
      )
    }

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
            podcastEpisodeState = null,
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
          id = UUID.randomUUID().toString(),
          title = directory.name,
          author = null,
          description = null,
          cover = "cover.jpg",
        )

      val metadataPath = "${directory.relativePath}/.lissen-book.json"
      webdavClient.putTextIfAbsent(metadataPath, metadataAdapter.toJson(defaultMetadata))

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

    private fun isDetailCacheValid(
      cache: WebdavBookDetailCache,
      directory: WebdavResource,
    ): Boolean = isValidationSame(cache.directoryEtag, cache.directoryLastModified, directory.eTag, directory.lastModified)

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
        coverEtag = coverEtag,
        coverLastModified = coverLastModified,
        introSkipSeconds = metadata.introSkipSecondsOrDefault(),
        outroSkipSeconds = metadata.outroSkipSecondsOrDefault(),
        resolvedCoverName = resolvedCoverName,
        isCoverMissing = isCoverMissing,
        isAdded = isAdded,
      )

    private fun IndexedBook.updateCoverState(transform: (WebdavBookIndexEntry) -> WebdavBookIndexEntry): IndexedBook =
      transform(toIndexEntry()).toIndexedBook()

    private fun updateIndexedBook(
      bookId: String,
      transform: (WebdavBookIndexEntry) -> WebdavBookIndexEntry,
    ) {
      val current = cachedBooks[bookId] ?: return
      putIndexedBook(current.updateCoverState(transform))
    }

    private fun putIndexedBook(updated: IndexedBook) {
      cachedBooks =
        cachedBooks
          .toMutableMap()
          .apply { put(updated.metadata.id, updated) }
      persistentCache.saveBookIndex(cachedBooks.values.map { it.toIndexEntry() })
    }

    private fun clearCachedCoverFiles() {
      (cachedBooks.keys + persistentCache.readBookIndex().map { it.bookId })
        .toSet()
        .forEach(::dropLocalCover)
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

    private fun compareByNaturalResourceName(): Comparator<WebdavResource> =
      Comparator { first, second ->
        compareNatural(first.name.lowercase(), second.name.lowercase())
      }

    private fun compareNatural(
      first: String,
      second: String,
    ): Int {
      val firstChunks = chunkRegex.findAll(first).map { it.value }.toList()
      val secondChunks = chunkRegex.findAll(second).map { it.value }.toList()
      val maxChunks = maxOf(firstChunks.size, secondChunks.size)

      for (index in 0 until maxChunks) {
        val firstChunk = firstChunks.getOrNull(index) ?: return -1
        val secondChunk = secondChunks.getOrNull(index) ?: return 1

        val firstNumber = firstChunk.toLongOrNull()
        val secondNumber = secondChunk.toLongOrNull()

        val comparison =
          when (firstNumber != null && secondNumber != null) {
            true -> firstNumber.compareTo(secondNumber)
            false -> firstChunk.compareTo(secondChunk)
          }

        if (comparison != 0) {
          return comparison
        }
      }

      return first.compareTo(second)
    }

    data class IndexedBook(
      val metadata: WebdavBookMetadata,
      val directory: WebdavResource,
      val metadataEtag: String?,
      val metadataLastModified: String?,
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
    )

    data class CoverValidation(
      val eTag: String?,
      val lastModified: String?,
    )

    companion object {
      const val WEBDAV_LIBRARY_ID = "webdav_library"
      const val WEBDAV_LIBRARY_TITLE = "书架"

      private val chunkRegex = Regex("""\d+|\D+""")
      private const val unresolvedDisplayDurationSeconds = 0.0
      private const val unresolvedTimelineDurationSeconds = 1.0
    }
  }
