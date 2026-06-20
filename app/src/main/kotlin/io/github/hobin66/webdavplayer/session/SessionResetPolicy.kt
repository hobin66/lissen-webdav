package io.github.hobin66.webdavplayer.session

import io.github.hobin66.webdavplayer.content.cache.persistent.ContentCachingService
import io.github.hobin66.webdavplayer.playback.service.PlaybackService

data class SessionResetActions(
  val shouldStopPlaybackService: Boolean,
  val shouldStopCachingService: Boolean,
  val shouldClearPlayingState: Boolean,
)

fun buildLogoutSessionResetActions(): SessionResetActions =
  SessionResetActions(
    shouldStopPlaybackService = true,
    shouldStopCachingService = true,
    shouldClearPlayingState = true,
  )

fun playbackServiceStopActions() =
  listOf(
      PlaybackService.ACTION_PAUSE,
      PlaybackService.ACTION_CANCEL_TIMER,
      PlaybackService.ACTION_STOP_SESSION,
    )

fun cachingServiceStopAction(): String = ContentCachingService.STOP_ALL_CACHING_ACTION
