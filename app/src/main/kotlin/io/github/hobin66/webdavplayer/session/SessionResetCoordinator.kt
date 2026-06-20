package io.github.hobin66.webdavplayer.session

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionResetCoordinator
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: WebdavMediaProvider,
    private val preferences: WebdavPlayerPreferences,
  ) {
    suspend fun logout() {
      val actions = buildLogoutSessionResetActions()

      if (actions.shouldClearPlayingState) {
        mediaRepository.clearPlayingBook()
      }
      if (actions.shouldStopPlaybackService) {
        stopPlaybackService()
      }
      if (actions.shouldStopCachingService) {
        stopCachingService()
      }
      mediaProvider.clearSessionState()
      preferences.clearPreferences()
    }

    private fun stopPlaybackService() {
      playbackServiceStopActions().forEach { action ->
        context.startService(
          Intent(context, io.github.hobin66.webdavplayer.playback.service.PlaybackService::class.java).apply {
            this.action = action
          },
        )
      }

      val playbackIntent = Intent(context, io.github.hobin66.webdavplayer.playback.service.PlaybackService::class.java)
      context.stopService(playbackIntent)
    }

    private fun stopCachingService() {
      val stopAllIntent =
        Intent(context, io.github.hobin66.webdavplayer.content.cache.persistent.ContentCachingService::class.java).apply {
          action = cachingServiceStopAction()
        }

      context.startService(stopAllIntent)
      context.stopService(Intent(context, io.github.hobin66.webdavplayer.content.cache.persistent.ContentCachingService::class.java))
    }
  }
