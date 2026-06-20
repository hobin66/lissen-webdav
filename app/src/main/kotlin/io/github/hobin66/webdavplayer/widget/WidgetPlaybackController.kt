package io.github.hobin66.webdavplayer.widget

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.MediaRepository
import io.github.hobin66.webdavplayer.playback.service.PlaybackEvent
import io.github.hobin66.webdavplayer.playback.service.PlaybackEvents
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class WidgetPlaybackController
  @Inject
  constructor(
    @ApplicationContext context: Context,
    private val mediaRepository: MediaRepository,
    private val sharedPreferences: WebdavPlayerPreferences,
  ) {
    private var playbackReadyAction: () -> Unit = {}

    init {
      CoroutineScope(Dispatchers.Main.immediate).launch {
        PlaybackEvents.events.collectLatest { event ->
          if (event == PlaybackEvent.PlaybackReady) {
            val book = sharedPreferences.getPlayingItem() ?: return@collectLatest
            book.let {
              playbackReadyAction
                .invoke()
                .also { playbackReadyAction = { } }
            }
          }
        }
      }
    }

    fun providePlayingItem() = mediaRepository.playingBook.value

    fun togglePlayPause() = mediaRepository.togglePlayPause()

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack(false)

    fun rewind() = mediaRepository.rewind()

    fun forward() = mediaRepository.forward()

    suspend fun prepareAndRun(
      itemId: String,
      onPlaybackReady: () -> Unit,
    ) {
      playbackReadyAction = onPlaybackReady
      mediaRepository.preparePlayback(bookId = itemId)
    }
  }
