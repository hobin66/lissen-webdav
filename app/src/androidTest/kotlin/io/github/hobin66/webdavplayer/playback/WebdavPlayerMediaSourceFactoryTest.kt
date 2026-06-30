package io.github.hobin66.webdavplayer.playback

import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hobin66.webdavplayer.content.ExternalCoverProvider
import io.github.hobin66.webdavplayer.playback.service.FileClip
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import io.github.hobin66.webdavplayer.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import io.github.hobin66.webdavplayer.playback.service.WebdavPlayerMediaSourceFactory
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebdavPlayerMediaSourceFactoryTest {
  private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
  private lateinit var webdavPlayerMediaSourceFactory: WebdavPlayerMediaSourceFactory

  @Before
  fun setUp() {
    mediaSourceFactory = mockk(relaxed = true)
    webdavPlayerMediaSourceFactory = WebdavPlayerMediaSourceFactory(mediaSourceFactory)
  }

  @Test
  fun no_exception_thrown_if_no_files() {
    val mediaSource =
      webdavPlayerMediaSourceFactory.createMediaSource(
        MediaItem
          .Builder()
          .setMediaId(WebdavPlayerMediaSourceFactory.MediaId("book-id", 5).toString())
          .setRequestMetadata(
            MediaItem.RequestMetadata
              .Builder()
              .setExtras(bundleOf(FILE_SEGMENTS to arrayListOf<FileClip>()))
              .build(),
          ).setMediaMetadata(
            MediaMetadata
              .Builder()
              .setAlbumTitle("title")
              .setTitle("chapter")
              .setArtist("book")
              .setIsBrowsable(false)
              .setIsPlayable(true)
              .setArtworkUri(ExternalCoverProvider.coverUri("book-id"))
              .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
              .setExtras(bundleOf(CHAPTER_START_MS to (500 * 1000).toLong()))
              .build(),
          ).build(),
      )
    assertNotNull(mediaSource)
  }
}
