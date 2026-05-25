package org.grakovne.lissen.playback

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    val preferences = provideAudioOffloadPreferences()

    assertEquals(
      TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
      preferences.audioOffloadMode,
    )
    assertFalse(preferences.isGaplessSupportRequired)
    assertFalse(preferences.isSpeedChangeSupportRequired)
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
