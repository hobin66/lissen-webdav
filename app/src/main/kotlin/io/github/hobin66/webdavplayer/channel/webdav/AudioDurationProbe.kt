package io.github.hobin66.webdavplayer.channel.webdav

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDurationProbe
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    suspend fun probeDurationSeconds(
      uri: Uri,
      headers: Map<String, String> = emptyMap(),
    ): Double? =
      try {
        runInterruptible(Dispatchers.IO) {
          val retriever = MediaMetadataRetriever()
          try {
            openRetriever(retriever, uri, headers)
            retriever
              .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
              ?.toLongOrNull()
              ?.takeIf { it > 0L }
              ?.let { it / 1000.0 }
          } finally {
            runCatching { retriever.release() }
          }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: InterruptedException) {
        throw CancellationException("Audio duration probe interrupted", error)
      } catch (error: Exception) {
        Timber.w(
          error,
          "Unable to probe audio duration (scheme=%s, host=%s)",
          uri.scheme,
          uri.host,
        )
        null
      }

    private fun openRetriever(
      retriever: MediaMetadataRetriever,
      uri: Uri,
      headers: Map<String, String>,
    ) {
      when (uri.scheme?.lowercase()) {
        "file" -> {
          val path = uri.path
          if (path.isNullOrBlank()) {
            error("Missing file path for $uri")
          }
          val file = File(path)
          if (!file.exists()) {
            error("File does not exist: $path")
          }
          retriever.setDataSource(file.absolutePath)
        }

        "http", "https" -> {
          retriever.setDataSource(uri.toString(), headers)
        }

        else -> {
          retriever.setDataSource(context, uri)
        }
      }
    }
  }
