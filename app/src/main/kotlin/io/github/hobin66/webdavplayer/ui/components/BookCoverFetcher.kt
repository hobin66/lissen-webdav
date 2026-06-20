package io.github.hobin66.webdavplayer.ui.components

import android.content.Context
import coil3.Extras
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.content.cache.persistent.LocalCacheRepository
import java.io.File
import javax.inject.Singleton

class BookCoverFetcher(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: WebdavMediaProvider,
  private val uri: String,
  private val options: Options,
) : Fetcher {
  override suspend fun fetch(): FetchResult? {
    val localOnly = options.extras[LocalOnlyKey] ?: false

    val response =
      when (localOnly) {
        true -> localCacheRepository.fetchBookCover(uri)
        false -> mediaChannel.fetchBookCover(uri)
      }

    return when (response) {
      is OperationResult.Error -> {
        null
      }

      is OperationResult.Success -> {
        val stream: File = response.data
        val imageSource =
          ImageSource(
            file = stream.toOkioPath(),
            fileSystem = FileSystem.SYSTEM,
          )

        SourceFetchResult(
          source = imageSource,
          mimeType = null,
          dataSource = coil3.decode.DataSource.DISK,
        )
      }
    }
  }

  companion object {
    val LocalOnlyKey = Extras.Key(false)
  }
}

class BookCoverFetcherFactory(
  private val localCacheRepository: LocalCacheRepository,
  private val dataProvider: WebdavMediaProvider,
) : Fetcher.Factory<Uri> {
  override fun create(
    data: Uri,
    options: Options,
    imageLoader: ImageLoader,
  ): BookCoverFetcher = BookCoverFetcher(localCacheRepository, dataProvider, data.toString(), options)
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
  @Singleton
  @Provides
  fun provideBookCoverFetcherFactory(
    localCacheRepository: LocalCacheRepository,
    mediaChannel: WebdavMediaProvider,
  ): BookCoverFetcherFactory = BookCoverFetcherFactory(localCacheRepository, mediaChannel)

  @Singleton
  @Provides
  fun provideCustomImageLoader(
    @ApplicationContext context: Context,
    bookCoverFetcherFactory: BookCoverFetcherFactory,
  ): ImageLoader =
    ImageLoader
      .Builder(context)
      .components { add(bookCoverFetcherFactory) }
      .build()
}
