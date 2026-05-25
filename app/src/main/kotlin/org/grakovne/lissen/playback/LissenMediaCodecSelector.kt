package org.grakovne.lissen.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException

@UnstableApi
class LissenMediaCodecSelector : MediaCodecSelector {
  @Throws(DecoderQueryException::class)
  override fun getDecoderInfos(
    mimeType: String,
    requiresSecureDecoder: Boolean,
    requiresTunnelingDecoder: Boolean,
  ) = MediaCodecUtil
    .getDecoderInfos(
      mimeType,
      requiresSecureDecoder,
      requiresTunnelingDecoder,
    ).let { decoderInfos ->
      when (shouldPreferHardwareAudioDecoder(mimeType)) {
        true -> preferHardwareAudioDecoders(decoderInfos)
        false -> decoderInfos
      }
    }
}
