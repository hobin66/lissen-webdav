package io.github.hobin66.webdavplayer.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.hobin66.webdavplayer.channel.common.createOkHttpClient
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.cache.PlaybackStreamCache
import timber.log.Timber

@OptIn(UnstableApi::class)
class WebdavPlayerDataSourceFactory(
  private val baseContext: Context,
  private val sharedPreferences: WebdavPlayerPreferences,
  private val mediaProvider: WebdavMediaProvider,
  private val streamCache: PlaybackStreamCache,
) : DataSource.Factory {
  private val upstreamFactory by lazy {
    OkHttpDataSource
      .Factory(
        createOkHttpClient(preferences = sharedPreferences),
      )
  }

  override fun createDataSource(): DataSource {
    val remoteFactory =
      streamCache.cacheOrNull()?.let { cache ->
        CacheDataSource
          .Factory()
          .setCache(cache)
          .setCacheKeyFactory(streamCache.cacheKeyFactory)
          .setUpstreamDataSourceFactory(upstreamFactory)
          .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
      } ?: upstreamFactory
    // DefaultDataSource routes file/content URIs locally; only HTTP(S) uses remoteFactory.
    val actualDataSource = DefaultDataSource.Factory(baseContext, remoteFactory).createDataSource()

    return object : DataSource by actualDataSource {
      override fun open(dataSpec: DataSpec): Long {
        val originalUri = dataSpec.uri
        val resolution = resolvePlaybackUri(originalUri)
        val resolvedUri = resolution?.resolvedUri ?: originalUri

        resolution?.let {
          Timber.d(
            "Resolved playback URI (scheme=%s, host=%s) for itemId=%s fileId=%s",
            resolvedUri.scheme,
            resolvedUri.host,
            it.itemId,
            it.fileId,
          )
        }

        return dataSpec
          .buildUpon()
          .setUri(resolvedUri)
          .apply {
            resolution?.let { setKey("${it.itemId}\u0000${it.fileId}") }
          }
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
