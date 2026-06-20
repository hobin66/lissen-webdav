package io.github.hobin66.webdavplayer.session

import io.github.hobin66.webdavplayer.content.cache.persistent.ContentCachingService
import io.github.hobin66.webdavplayer.playback.service.PlaybackService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionResetPolicyTest {
  @Test
  fun `logout session reset stops playback caching and clears playing state`() {
    val actions = buildLogoutSessionResetActions()

    assertTrue(actions.shouldClearPlayingState)
    assertTrue(actions.shouldStopPlaybackService)
    assertTrue(actions.shouldStopCachingService)
  }

  @Test
  fun `playback service stop actions cancel timer pause playback and clear session`() {
    assertEquals(
      listOf(
        PlaybackService.ACTION_PAUSE,
        PlaybackService.ACTION_CANCEL_TIMER,
        PlaybackService.ACTION_STOP_SESSION,
      ),
      playbackServiceStopActions(),
    )
  }

  @Test
  fun `caching stop action uses stop all marker`() {
    assertEquals(ContentCachingService.STOP_ALL_CACHING_ACTION, cachingServiceStopAction())
  }
}
