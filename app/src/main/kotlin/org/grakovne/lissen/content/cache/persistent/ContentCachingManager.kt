package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.common.copyTo
import org.grakovne.lissen.content.cache.common.findRelatedFiles
import org.grakovne.lissen.content.cache.common.withBlur
import org.grakovne.lissen.content.cache.common.writeToFile
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ContentCachingManager
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: OfflineBookStorageProperties,
    private val preferences: LissenSharedPreferences,
  ) {
    fun cacheMediaItem(
      mediaItem: DetailedItem,
      option: DownloadOption,
      channel: MediaChannel,
      currentTotalPosition: Double,
    ) = flow {
      try {
        val context = coroutineContext

        val requestedChapters =
          calculateRequestedChapters(
            book = mediaItem,
            option = option,
            currentTotalPosition = currentTotalPosition,
          )

        val existingChapters =
          bookRepository
            .fetchBook(bookId = mediaItem.id)
            ?.chapters
            ?.filter { it.available }
            ?: emptyList()

        val cachingChapters = requestedChapters - existingChapters.toSet()

        val requestedFiles = findRequestedFiles(mediaItem, cachingChapters)

        if (requestedFiles.isEmpty()) {
          emit(CacheState(CacheStatus.Completed))
          return@flow
        }

        emit(CacheState(CacheStatus.Caching))

        val mediaCachingResult =
          cacheBookMedia(
            mediaItem.id,
            requestedFiles,
            channel,
          ) { withContext(context) { emit(CacheState(CacheStatus.Caching, it)) } }

        val coverCachingResult = cacheBookCover(mediaItem, channel)
        val librariesCachingResult = cacheLibraries(channel)

        when {
          listOf(
            mediaCachingResult,
            coverCachingResult,
            librariesCachingResult,
          ).all { it.status == CacheStatus.Completed } -> {
            cacheBookInfo(mediaItem, requestedChapters)
            emit(CacheState(CacheStatus.Completed))
          }

          else -> {
            cachingChapters.map { dropCache(mediaItem, it) }
            emit(CacheState(CacheStatus.Error))
          }
        }
      } catch (ex: CancellationException) {
        throw ex
      } catch (ex: Exception) {
        Timber.e(ex, "Unable to cache media item ${mediaItem.id}")
        emit(CacheState(CacheStatus.Error))
      }
    }

    suspend fun dropCache(
      item: DetailedItem,
      chapter: PlayingChapter,
    ) {
      bookRepository
        .cacheBook(
          book = item.withRuntimeBookSkipSettings(),
          fetchedChapters = emptyList(),
          droppedChapters = listOf(chapter),
        )

      findRequestedFiles(item, listOf(chapter))
        .forEach { file ->
          val binaryContent = properties.provideMediaCachePatch(item.id, file.id)

          if (binaryContent.exists()) {
            binaryContent.delete()
          }
        }
    }

    suspend fun dropCache(itemId: String) {
      bookRepository.removeBook(itemId)

      val cachedContent: File = properties.provideBookCache(itemId)

      if (cachedContent.exists()) {
        cachedContent.deleteRecursively()
      }
    }

    fun hasMetadataCached(mediaItemId: String) = bookRepository.provideCacheState(mediaItemId)

    fun hasMetadataCached(
      mediaItemId: String,
      chapterId: String,
    ) = bookRepository.provideCacheState(mediaItemId, chapterId)

    private suspend fun cacheBookMedia(
      bookId: String,
      files: List<BookFile>,
      channel: MediaChannel,
      onProgress: suspend (Double) -> Unit,
    ): CacheState =
      withContext(Dispatchers.IO) {
        val client = createOkHttpClient(preferences = preferences)

        val totalFileSize = files.mapNotNull { it.size }.sum()
        val reportingSizeThreshold = totalFileSize / 100.0
        var fetchedFileSize = 0.0

        files.forEach { file ->
          val uri = channel.provideFileUri(bookId, file.id)
          if (uri == Uri.EMPTY || uri.scheme.isNullOrBlank()) {
            Timber.e("Unable to cache media content: invalid uri for fileId=${file.id}")
            return@withContext CacheState(CacheStatus.Error)
          }

          val request =
            runCatching { Request.Builder().url(uri.toString()).build() }
              .onFailure { Timber.e(it, "Unable to build cache request for uri=$uri") }
              .getOrElse { return@withContext CacheState(CacheStatus.Error) }
          val response =
            runCatching { client.newCall(request).execute() }
              .onFailure { Timber.e(it, "Unable to execute cache request for uri=$uri") }
              .getOrElse { return@withContext CacheState(CacheStatus.Error) }

          response.use {
            if (!it.isSuccessful) {
              Timber.e("Unable to cache media content: $it")
              return@withContext CacheState(CacheStatus.Error)
            }

            val body = it.body
            val dest = properties.provideMediaCachePatch(bookId, file.id)
            dest.parentFile?.mkdirs()

            try {
              dest.outputStream().use { output ->
                body.byteStream().use { input ->
                  var lastReportedSize = 0.0
                  input.copyTo(output) {
                    fetchedFileSize += it

                    if (totalFileSize > 0 && fetchedFileSize - lastReportedSize >= reportingSizeThreshold) {
                      lastReportedSize = fetchedFileSize
                      onProgress(fetchedFileSize / totalFileSize.toDouble())
                    }
                  }
                }
              }
            } catch (ex: Exception) {
              Timber.e(ex, "Unable to write cache file for fileId=${file.id}")
              return@withContext CacheState(CacheStatus.Error)
            }
          }
        }

        CacheState(CacheStatus.Completed)
      }

    private suspend fun cacheBookCover(
      book: DetailedItem,
      channel: MediaChannel,
    ): CacheState {
      val file = properties.provideBookCoverPath(book.id)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(book.id)
          .fold(
            onSuccess = { cover ->
              try {
                cover
                  .withBlur(context)
                  .writeToFile(file)
              } catch (ex: Exception) {
                return@fold CacheState(CacheStatus.Error)
              }
            },
            onFailure = {
            },
          )

        CacheState(CacheStatus.Completed)
      }
    }

    private suspend fun cacheBookInfo(
      book: DetailedItem,
      fetchedChapters: List<PlayingChapter>,
    ): CacheState =
      bookRepository
        .cacheBook(book.withRuntimeBookSkipSettings(), fetchedChapters, emptyList())
        .let { CacheState(CacheStatus.Completed) }

    private suspend fun cacheLibraries(channel: MediaChannel): CacheState =
      channel
        .fetchLibraries()
        .foldAsync(
          onSuccess = {
            libraryRepository.cacheLibraries(it)
            CacheState(CacheStatus.Completed)
          },
          onFailure = {
            CacheState(CacheStatus.Error)
          },
        )

    private fun findRequestedFiles(
      book: DetailedItem,
      requestedChapters: List<PlayingChapter>,
    ): List<BookFile> =
      requestedChapters
        .flatMap { findRelatedFiles(it, book.files) }
        .distinctBy { it.id }
  }
