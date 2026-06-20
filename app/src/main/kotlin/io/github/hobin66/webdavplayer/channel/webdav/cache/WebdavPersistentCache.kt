package io.github.hobin66.webdavplayer.channel.webdav.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.hobin66.webdavplayer.channel.webdav.WebdavPathCodec
import io.github.hobin66.webdavplayer.common.moshi
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavPersistentCache
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    private val indexStoreAdapter = moshi.adapter(WebdavBookIndexStore::class.java)
    private val detailCacheAdapter = moshi.adapter(WebdavBookDetailCache::class.java)

    private val rootFolder: File by lazy { context.filesDir.resolve(ROOT_FOLDER).also { it.mkdirs() } }
    private val detailsFolder: File by lazy { rootFolder.resolve(DETAILS_FOLDER).also { it.mkdirs() } }
    private val indexFile: File by lazy { rootFolder.resolve(INDEX_FILE_NAME) }

    private val indexMutex = Mutex()
    private val detailsMutex = Mutex()

    @Volatile
    private var cachedIndex: List<WebdavBookIndexEntry>? = null

    suspend fun readBookIndex(): List<WebdavBookIndexEntry> {
      cachedIndex?.let { return it }
      return withContext(Dispatchers.IO) {
        indexMutex.withLock {
          cachedIndex?.let { return@withLock it }
          val loaded = loadBookIndexFromDisk()
          cachedIndex = loaded
          loaded
        }
      }
    }

    suspend fun saveBookIndex(entries: List<WebdavBookIndexEntry>) {
      val snapshot = entries.toList()
      cachedIndex = snapshot
      withContext(Dispatchers.IO) {
        indexMutex.withLock {
          runCatching {
            rootFolder.mkdirs()
            val payload = WebdavBookIndexStore(snapshot)
            indexFile.writeText(indexStoreAdapter.toJson(payload))
          }.onFailure { Timber.w(it, "Unable to persist WebDAV index cache") }
        }
      }
    }

    suspend fun readBookDetail(bookId: String): WebdavBookDetailCache? =
      withContext(Dispatchers.IO) {
        detailsMutex.withLock {
          val file = provideDetailFile(bookId)
          val json = runCatching { file.takeIf(File::exists)?.readText() }.getOrNull() ?: return@withLock null
          runCatching { detailCacheAdapter.fromJson(json) }
            .onFailure { Timber.w(it, "Unable to parse detail cache for $bookId") }
            .getOrNull()
        }
      }

    suspend fun saveBookDetail(cache: WebdavBookDetailCache) {
      withContext(Dispatchers.IO) {
        detailsMutex.withLock {
          runCatching {
            detailsFolder.mkdirs()
            provideDetailFile(cache.bookId).writeText(detailCacheAdapter.toJson(cache))
          }.onFailure { Timber.w(it, "Unable to persist detail cache for ${cache.bookId}") }
        }
      }
    }

    suspend fun removeBookDetail(bookId: String) {
      withContext(Dispatchers.IO) {
        detailsMutex.withLock {
          runCatching { provideDetailFile(bookId).delete() }
            .onFailure { Timber.w(it, "Unable to remove detail cache for $bookId") }
        }
      }
    }

    suspend fun clearBookDetails() {
      withContext(Dispatchers.IO) {
        detailsMutex.withLock {
          runCatching {
            if (detailsFolder.exists()) {
              detailsFolder.deleteRecursively()
            }
            detailsFolder.mkdirs()
          }.onFailure { Timber.w(it, "Unable to clear WebDAV detail cache") }
        }
      }
    }

    suspend fun clearAll() {
      cachedIndex = emptyList()
      withContext(Dispatchers.IO) {
        indexMutex.withLock {
          detailsMutex.withLock {
            runCatching {
              if (rootFolder.exists()) {
                rootFolder.deleteRecursively()
              }
            }.onFailure { Timber.w(it, "Unable to clear WebDAV persistent cache") }
          }
        }
      }
    }

    private fun loadBookIndexFromDisk(): List<WebdavBookIndexEntry> {
      val json = runCatching { indexFile.takeIf(File::exists)?.readText() }.getOrNull() ?: return emptyList()
      return runCatching { indexStoreAdapter.fromJson(json)?.items ?: emptyList() }
        .onFailure { Timber.w(it, "Unable to parse WebDAV index cache") }
        .getOrDefault(emptyList())
    }

    private fun provideDetailFile(bookId: String): File = detailsFolder.resolve("${WebdavPathCodec.encode(bookId)}.json")

    private companion object {
      const val ROOT_FOLDER = "webdav_cache"
      const val DETAILS_FOLDER = "book_details"
      const val INDEX_FILE_NAME = "books_index.json"
    }
  }
