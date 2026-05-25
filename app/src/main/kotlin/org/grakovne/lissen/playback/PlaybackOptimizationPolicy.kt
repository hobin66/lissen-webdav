package org.grakovne.lissen.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo

fun providePlaybackAudioAttributes(): AudioAttributes =
  AudioAttributes
    .Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
    .build()

@UnstableApi
fun provideAudioOffloadPreferences(): TrackSelectionParameters.AudioOffloadPreferences =
  TrackSelectionParameters
    .AudioOffloadPreferences
    .Builder()
    .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
    .setIsGaplessSupportRequired(false)
    .setIsSpeedChangeSupportRequired(false)
    .build()

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
