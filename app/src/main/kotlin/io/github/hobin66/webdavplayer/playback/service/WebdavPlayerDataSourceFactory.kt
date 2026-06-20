package io.github.hobin66.webdavplayer.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.hobin66.webdavplayer.channel.common.createOkHttpClient
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import timber.log.Timber

@OptIn(UnstableApi::class)
class WebdavPlayerDataSourceFactory(
  private val baseContext: Context,
  private val sharedPreferences: WebdavPlayerPreferences,
  private val mediaProvider: WebdavMediaProvider,
) : DataSource.Factory {
  private val upstreamFactory by lazy {
    OkHttpDataSource
      .Factory(
        createOkHttpClient(preferences = sharedPreferences),
      )
  }

  private val defaultFactory by lazy { DefaultDataSource.Factory(baseContext, upstreamFactory) }

  override fun createDataSource(): DataSource {
    val actualDataSource = defaultFactory.createDataSource()

    return object : DataSource by actualDataSource {
      override fun open(dataSpec: DataSpec): Long {
        val originalUri = dataSpec.uri
        val resolution = resolvePlaybackUri(originalUri)
        val resolvedUri = resolution?.resolvedUri ?: originalUri

        resolution?.let {
          Timber.d("Resolved Uri: %s for itemId=%s fileId=%s", resolvedUri, it.itemId, it.fileId)
        }

        return dataSpec
          .buildUpon()
          .setUri(resolvedUri)
          .build()
          .let { actualDataSource.open(it) }
      }

      private fun resolvePlaybackUri(uri: android.net.Uri): ResolvedPlaybackUri? {
        val (itemId, fileId) = unapply(uri) ?: return null

        val resolvedUri =
          mediaProvider
            .provideFileUri(itemId, fileId)
            .fold(
              onSuccess = { it },
              onFailure = { uri },
            )

        return ResolvedPlaybackUri(
          itemId = itemId,
          fileId = fileId,
          resolvedUri = resolvedUri,
        )
      }
    }
  }

  private data class ResolvedPlaybackUri(
    val itemId: String,
    val fileId: String,
    val resolvedUri: android.net.Uri,
  )
}
