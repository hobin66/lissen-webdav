package org.grakovne.lissen.playback

import android.content.Context
import android.os.PowerManager
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.R
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.LissenDataSourceFactory
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import timber.log.Timber
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun provideExoPlayer(
    @ApplicationContext context: Context,
    sharedPreferences: LissenSharedPreferences,
    mediaProvider: LissenMediaProvider,
  ): ExoPlayer {
    val mediaCodecQueueingMode = sharedPreferences.getMediaCodecQueueingMode()
    val renderersFactory =
      when (sharedPreferences.getSoftwareCodecsEnabled()) {
        true -> {
          SoftwareCodecRendersFactory(context)
        }

        false -> {
          DefaultRenderersFactory(context)
            .apply {
              setMediaCodecSelector(LissenMediaCodecSelector())
              if (shouldForceSynchronousMediaCodecQueueing(mediaCodecQueueingMode)) {
                forceDisableMediaCodecAsynchronousQueueing()
                Timber.i("MediaCodec async queueing disabled (mode=%s)", mediaCodecQueueingMode)
              }
            }
        }
      }

    val player =
      ExoPlayer
        .Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(providePlaybackAudioAttributes(), true)
        .setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
        .experimentalSetDynamicSchedulingEnabled(true)
        .setRenderersFactory(renderersFactory)
        .setMediaSourceFactory(
          LissenMediaSourceFactory(
            mediaSourceFactory =
              DefaultMediaSourceFactory(
                LissenDataSourceFactory(
                  baseContext = context,
                  sharedPreferences = sharedPreferences,
                  mediaProvider = mediaProvider,
                ),
              ),
          ),
        ).build()

    player.trackSelectionParameters =
      player.trackSelectionParameters
        .buildUpon()
        .setAudioOffloadPreferences(provideAudioOffloadPreferences(sharedPreferences.getPlaybackVolumeBoost()))
        .build()

    player.addAnalyticsListener(mediaCodecListener(context))
    return player
  }
}

@UnstableApi
private fun mediaCodecListener(context: Context): AnalyticsListener =
  object : AnalyticsListener {
    override fun onAudioDecoderInitialized(
      eventTime: AnalyticsListener.EventTime,
      decoderName: String,
      initializedTimestampMs: Long,
      initializationDurationMs: Long,
    ) {
      Timber.d("Audio decoder initialized: $decoderName")
    }

    override fun onAudioCodecError(
      eventTime: AnalyticsListener.EventTime,
      audioCodecError: Exception,
    ) {
      Toast
        .makeText(
          context,
          context.getString(R.string.codes_not_supported_warning_toast),
          LENGTH_SHORT,
        ).show()

      super.onAudioCodecError(eventTime, audioCodecError)
    }
  }
