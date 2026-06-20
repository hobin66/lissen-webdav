package org.grakovne.lissen.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import org.grakovne.lissen.common.PlaybackVolumeBoost

private const val PLAYBACK_PROGRESS_UPDATE_INTERVAL_MS = 1_000L
private const val POSITION_REFRESH_EPSILON_SECONDS = 0.05

fun providePlaybackAudioAttributes(): AudioAttributes =
  AudioAttributes
    .Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
    .build()

@UnstableApi
fun provideAudioOffloadPreferences(volumeBoost: PlaybackVolumeBoost): TrackSelectionParameters.AudioOffloadPreferences =
  TrackSelectionParameters
    .AudioOffloadPreferences
    .Builder()
    .setAudioOffloadMode(resolveAudioOffloadMode(volumeBoost))
    .setIsGaplessSupportRequired(false)
    .setIsSpeedChangeSupportRequired(false)
    .build()

@UnstableApi
fun resolveAudioOffloadMode(volumeBoost: PlaybackVolumeBoost): Int =
  when (volumeBoost) {
    PlaybackVolumeBoost.DISABLED -> TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
    else -> TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
  }

fun resolvePlaybackProgressUpdateIntervalMs(isPlaying: Boolean): Long? =
  when (isPlaying) {
    true -> PLAYBACK_PROGRESS_UPDATE_INTERVAL_MS
    false -> null
  }

fun resolveTotalPositionSeconds(
  chapterStartOffsetMs: Long?,
  chapterPositionMs: Long,
): Double? =
  chapterStartOffsetMs
    ?.takeIf { it >= 0L }
    ?.let { chapterStartMs ->
      (chapterStartMs + chapterPositionMs.coerceAtLeast(0L)) / 1000.0
    }

fun shouldRefreshPlaybackProgressOnPositionDiscontinuity(
  isPlaying: Boolean,
  previousTotalPositionSeconds: Double?,
  currentTotalPositionSeconds: Double?,
): Boolean {
  if (isPlaying) {
    return false
  }

  val next = currentTotalPositionSeconds ?: return false
  val previous = previousTotalPositionSeconds ?: return true

  return kotlin.math.abs(previous - next) > POSITION_REFRESH_EPSILON_SECONDS
}

fun shouldForceSynchronousMediaCodecQueueing(mode: MediaCodecQueueingMode): Boolean =
  when (mode) {
    MediaCodecQueueingMode.AUTOMATIC -> false
    MediaCodecQueueingMode.FORCE_SYNCHRONOUS -> true
    MediaCodecQueueingMode.FORCE_ASYNCHRONOUS -> false
  }

fun shouldPreferHardwareAudioDecoder(mimeType: String): Boolean = MimeTypes.isAudio(mimeType)

fun preferHardwareAudioDecoders(decoderInfos: List<MediaCodecInfo>): List<MediaCodecInfo> =
  decoderInfos.sortedByDescending(::scoreAudioDecoderPreference)

private fun scoreAudioDecoderPreference(decoderInfo: MediaCodecInfo): Int =
  buildList {
    add(if (decoderInfo.hardwareAccelerated) 100 else 0)
    add(if (decoderInfo.vendor) 10 else 0)
    add(if (!decoderInfo.softwareOnly) 5 else 0)
    add(
      when {
        decoderInfo.name.contains(".hw.") -> 3
        decoderInfo.name.startsWith("c2.android.") -> -3
        decoderInfo.name.startsWith("OMX.google.") -> -4
        else -> 0
      },
    )
  }.sum()
