package org.grakovne.lissen.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber

@OptIn(UnstableApi::class)
class LissenDataSourceFactory(
  private val baseContext: Context,
  private val sharedPreferences: LissenSharedPreferences,
  private val mediaProvider: LissenMediaProvider,
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
        val (itemId, fileId) = unapply(dataSpec.uri) ?: return 0
				
        val resolvedUri =
          mediaProvider
            .provideFileUri(itemId, fileId)
            .fold(
              onSuccess = { it },
              onFailure = { dataSpec.uri },
            )

        Timber.d("Resolved Uri: $resolvedUri for itemId = $itemId and fileId = $fileId")
				
        return dataSpec
          .buildUpon()
          .setUri(resolvedUri)
          .build()
          .let { actualDataSource.open(it) }
      }
    }
  }
}
