package org.grakovne.lissen.channel.webdav.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.channel.webdav.WebdavPathCodec
import org.grakovne.lissen.common.moshi
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavPersistentCache
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    private val indexStoreAdapter = moshi.adapter(WebdavBookIndexStore::class.java)
    private val detailCacheAdapter = moshi.adapter(WebdavBookDetailCache::class.java)

    private val rootFolder: File by lazy { context.filesDir.resolve(ROOT_FOLDER).also { it.mkdirs() } }
    private val detailsFolder: File by lazy { rootFolder.resolve(DETAILS_FOLDER).also { it.mkdirs() } }
    private val indexFile: File by lazy { rootFolder.resolve(INDEX_FILE_NAME) }

    @Synchronized
    fun readBookIndex(): List<WebdavBookIndexEntry> {
      val json = runCatching { indexFile.takeIf(File::exists)?.readText() }.getOrNull() ?: return emptyList()
      return runCatching { indexStoreAdapter.fromJson(json)?.items ?: emptyList() }
        .onFailure { Timber.w(it, "Unable to parse WebDAV index cache") }
        .getOrDefault(emptyList())
    }

    @Synchronized
    fun saveBookIndex(entries: List<WebdavBookIndexEntry>) {
      runCatching {
        rootFolder.mkdirs()
        val payload = WebdavBookIndexStore(entries)
        indexFile.writeText(indexStoreAdapter.toJson(payload))
      }.onFailure { Timber.w(it, "Unable to persist WebDAV index cache") }
    }

    @Synchronized
    fun readBookDetail(bookId: String): WebdavBookDetailCache? {
      val file = provideDetailFile(bookId)
      val json = runCatching { file.takeIf(File::exists)?.readText() }.getOrNull() ?: return null
      return runCatching { detailCacheAdapter.fromJson(json) }
        .onFailure { Timber.w(it, "Unable to parse detail cache for $bookId") }
        .getOrNull()
    }

    @Synchronized
    fun saveBookDetail(cache: WebdavBookDetailCache) {
      runCatching {
        detailsFolder.mkdirs()
        provideDetailFile(cache.bookId).writeText(detailCacheAdapter.toJson(cache))
      }.onFailure { Timber.w(it, "Unable to persist detail cache for ${cache.bookId}") }
    }

    @Synchronized
    fun removeBookDetail(bookId: String) {
      runCatching { provideDetailFile(bookId).delete() }
        .onFailure { Timber.w(it, "Unable to remove detail cache for $bookId") }
    }

    @Synchronized
    fun clearBookDetails() {
      runCatching {
        if (detailsFolder.exists()) {
          detailsFolder.deleteRecursively()
        }
        detailsFolder.mkdirs()
      }.onFailure { Timber.w(it, "Unable to clear WebDAV detail cache") }
    }

    private fun provideDetailFile(bookId: String): File = detailsFolder.resolve("${WebdavPathCodec.encode(bookId)}.json")

    private companion object {
      const val ROOT_FOLDER = "webdav_cache"
      const val DETAILS_FOLDER = "book_details"
      const val INDEX_FILE_NAME = "books_index.json"
    }
  }
