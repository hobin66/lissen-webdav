package io.github.hobin66.webdavplayer.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.common.PlaybackVolumeBoost
import io.github.hobin66.webdavplayer.common.RunningComponent
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PlaybackEnhancerService
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val sharedPreferences: WebdavPlayerPreferences,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var enhancer: LoudnessEnhancer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
      player.addListener(
        object : Player.Listener {
          override fun onAudioSessionIdChanged(id: Int) {
            attachEnhancer(id, sharedPreferences.getPlaybackVolumeBoost())
          }
        },
      )
      attachEnhancer(player.audioSessionId, sharedPreferences.getPlaybackVolumeBoost())

      scope.launch {
        sharedPreferences.playbackVolumeBoostFlow.collectLatest { updateGain(it) }
      }

      updateGain(sharedPreferences.getPlaybackVolumeBoost())
    }

    @OptIn(UnstableApi::class)
    private fun attachEnhancer(
      sessionId: Int,
      boost: PlaybackVolumeBoost,
    ) {
      enhancer?.release()
      enhancer = null

      if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
      if (boost == PlaybackVolumeBoost.DISABLED) return

      try {
        enhancer = LoudnessEnhancer(sessionId)
        enhancer?.enabled = true
        enhancer?.setTargetGain(boostToMb(boost))
      } catch (ex: Exception) {
        Timber.e("Unable to attach LoudnessEnhancer due to ${ex.message}")
      }
    }

    private fun updateGain(value: PlaybackVolumeBoost) {
      updateAudioOffload(value)

      try {
        when (value) {
          PlaybackVolumeBoost.DISABLED -> {
            enhancer?.enabled = false
            enhancer?.release()
            enhancer = null
          }

          else -> {
            if (enhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
              attachEnhancer(player.audioSessionId, value)
              return
            }
            enhancer?.enabled = true
            enhancer?.setTargetGain(boostToMb(value))
          }
        }
      } catch (ex: Exception) {
        Timber.e("Unable update volume gain with $value due to: $ex")
      }
    }

    @OptIn(UnstableApi::class)
    private fun updateAudioOffload(value: PlaybackVolumeBoost) {
      player.trackSelectionParameters =
        player.trackSelectionParameters
          .buildUpon()
          .setAudioOffloadPreferences(provideAudioOffloadPreferences(value))
          .build()
    }

    private fun boostToMb(value: PlaybackVolumeBoost): Int =
      when (value) {
        PlaybackVolumeBoost.DISABLED -> 0
        PlaybackVolumeBoost.LOW -> dbToMb(3f)
        PlaybackVolumeBoost.MEDIUM -> dbToMb(6f)
        PlaybackVolumeBoost.HIGH -> dbToMb(12f)
        PlaybackVolumeBoost.MAX -> dbToMb(20f)
      }

    private fun dbToMb(db: Float): Int = (db * 100f).roundToInt()
  }
