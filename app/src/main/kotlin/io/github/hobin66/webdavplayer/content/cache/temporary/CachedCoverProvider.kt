package io.github.hobin66.webdavplayer.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.hobin66.webdavplayer.channel.common.MediaChannel
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.content.cache.common.withBlur
import io.github.hobin66.webdavplayer.content.cache.common.writeToFile
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedCoverProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val properties: ShortTermCacheStorageProperties,
  ) {
    suspend fun provideCover(
      channel: MediaChannel,
      itemId: String,
    ): OperationResult<File> =
      when (val cover = fetchCachedCover(itemId)) {
        null -> cacheCover(channel, itemId).also { Timber.d("Caching cover $itemId") }
        else -> cover.let { OperationResult.Success(it) }.also { Timber.d("Fetched cached $itemId") }
      }

    fun clearCache() =
      properties
        .provideCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear cover short-term cache") }

    private fun fetchCachedCover(itemId: String): File? {
      val file = properties.provideCoverPath(itemId)

      return when (file.exists()) {
        true -> file
        else -> null
      }
    }

    private suspend fun cacheCover(
      channel: MediaChannel,
      itemId: String,
    ): OperationResult<File> {
      val dest = properties.provideCoverPath(itemId)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(itemId)
          .fold(
            onSuccess = { source ->
              val blurred = source.withBlur(context)
              dest.parentFile?.mkdirs()

              blurred.writeToFile(dest)
              OperationResult.Success(dest)
            },
            onFailure = { OperationResult.Error(OperationError.InternalError, it.message) },
          )
      }
    }
  }
