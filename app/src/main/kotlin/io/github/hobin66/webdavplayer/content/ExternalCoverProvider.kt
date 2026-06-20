package io.github.hobin66.webdavplayer.content

import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import io.github.hobin66.webdavplayer.BuildConfig
import io.github.hobin66.webdavplayer.R

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WebdavMediaProviderEntryPoint {
  fun getWebdavMediaProvider(): WebdavMediaProvider
}

class ExternalCoverProvider : FileProvider() {
  companion object {
    fun coverUri(bookId: String) = "content://${BuildConfig.APPLICATION_ID}.cover/cover/$bookId".toUri()
  }

  private val webdavMediaProvider: WebdavMediaProvider
    get() {
      val appContext = requireNotNull(context).applicationContext
      return EntryPointAccessors
        .fromApplication(
          appContext,
          WebdavMediaProviderEntryPoint::class.java,
        ).getWebdavMediaProvider()
    }

  override fun openAssetFile(
    uri: Uri,
    mode: String,
  ): AssetFileDescriptor? {
    val bookId =
      uri.lastPathSegment
        ?: return super.openAssetFile(uri, mode)

    return runBlocking(Dispatchers.IO) {
      webdavMediaProvider
        .fetchBookCover(bookId = bookId)
        .fold(
          onSuccess = { super.openAssetFile(uri, mode) },
          onFailure = { context?.resources?.openRawResourceFd(R.raw.cover_fallback_png) },
        )
    }
  }
}
