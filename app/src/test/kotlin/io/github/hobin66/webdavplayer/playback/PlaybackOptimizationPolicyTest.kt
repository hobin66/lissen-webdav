package io.github.hobin66.webdavplayer.playback

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import io.github.hobin66.webdavplayer.common.PlaybackVolumeBoost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackOptimizationPolicyTest {
  @Test
  fun `playback audio attributes keep speech content profile`() {
    val attributes = providePlaybackAudioAttributes()

    assertEquals(C.USAGE_MEDIA, attributes.usage)
    assertEquals(C.AUDIO_CONTENT_TYPE_SPEECH, attributes.contentType)
  }

  @Test
  fun `audio offload stays disabled by default for playback stability`() {
    val preferences = provideAudioOffloadPreferences(PlaybackVolumeBoost.DISABLED)

    assertEquals(
      TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
      preferences.audioOffloadMode,
    )
    assertFalse(preferences.isGaplessSupportRequired)
    assertFalse(preferences.isSpeedChangeSupportRequired)
  }

  @Test
  fun `audio offload is disabled while loudness enhancement is active`() {
    val preferences = provideAudioOffloadPreferences(PlaybackVolumeBoost.HIGH)

    assertEquals(
      TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
      preferences.audioOffloadMode,
    )
  }

  @Test
  fun `playback progress refresh runs once per second only while actively playing`() {
    assertEquals(1_000L, resolvePlaybackProgressUpdateIntervalMs(isPlaying = true))
    assertNull(resolvePlaybackProgressUpdateIntervalMs(isPlaying = false))
  }

  @Test
  fun `chapter metadata offset resolves total position without recomputing chapter sums`() {
    assertEquals(
      92.5,
      resolveTotalPositionSeconds(
        chapterStartOffsetMs = 90_000L,
        chapterPositionMs = 2_500L,
      ),
    )
  }

  @Test
  fun `missing chapter metadata offset does not resolve total position`() {
    assertNull(resolveTotalPositionSeconds(chapterStartOffsetMs = null, chapterPositionMs = 2_500L))
  }

  @Test
  fun `paused playback refreshes progress after a meaningful seek`() {
    assertTrue(
      shouldRefreshPlaybackProgressOnPositionDiscontinuity(
        isPlaying = false,
        previousTotalPositionSeconds = 12.0,
        currentTotalPositionSeconds = 42.0,
      ),
    )
  }

  @Test
  fun `active playback does not double refresh progress on discontinuity`() {
    assertFalse(
      shouldRefreshPlaybackProgressOnPositionDiscontinuity(
        isPlaying = true,
        previousTotalPositionSeconds = 12.0,
        currentTotalPositionSeconds = 42.0,
      ),
    )
  }

  @Test
  fun `tiny paused playback discontinuities do not trigger redundant refresh`() {
    assertFalse(
      shouldRefreshPlaybackProgressOnPositionDiscontinuity(
        isPlaying = false,
        previousTotalPositionSeconds = 12.0,
        currentTotalPositionSeconds = 12.02,
      ),
    )
  }

  @Test
  fun `first paused playback discontinuity refreshes when no previous progress exists`() {
    assertTrue(
      shouldRefreshPlaybackProgressOnPositionDiscontinuity(
        isPlaying = false,
        previousTotalPositionSeconds = null,
        currentTotalPositionSeconds = 5.0,
      ),
    )
  }

  @Test
  fun `automatic media codec queueing does not force synchronous mode by default`() {
    assertFalse(shouldForceSynchronousMediaCodecQueueing(MediaCodecQueueingMode.AUTOMATIC))
  }

  @Test
  fun `media codec queueing can be forced to synchronous mode`() {
    assertTrue(shouldForceSynchronousMediaCodecQueueing(MediaCodecQueueingMode.FORCE_SYNCHRONOUS))
  }

  @Test
  fun `media codec queueing can be forced to asynchronous mode`() {
    assertFalse(shouldForceSynchronousMediaCodecQueueing(MediaCodecQueueingMode.FORCE_ASYNCHRONOUS))
  }

  @Test
  fun `audio decoder ordering prefers hardware vendor codecs over generic software codecs`() {
    val hardwareAac =
      MediaCodecInfo.newInstance(
        "c2.qti.aac.hw.decoder",
        "audio/mp4a-latm",
        "audio/mp4a-latm",
        null,
        true,
        false,
        true,
        false,
        false,
      )
    val softwareAac =
      MediaCodecInfo.newInstance(
        "c2.android.aac.decoder",
        "audio/mp4a-latm",
        "audio/mp4a-latm",
        null,
        false,
        true,
        false,
        false,
        false,
      )

    val ordered = preferHardwareAudioDecoders(listOf(softwareAac, hardwareAac))

    assertEquals(
      listOf("c2.qti.aac.hw.decoder", "c2.android.aac.decoder"),
      ordered.map { it.name },
    )
  }
}
