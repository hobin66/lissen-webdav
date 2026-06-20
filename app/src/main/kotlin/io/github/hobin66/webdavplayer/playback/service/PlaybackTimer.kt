package io.github.hobin66.webdavplayer.playback.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.TimerOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackTimer
  @Inject
  constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val exoPlayer: ExoPlayer,
  ) {
    private val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val uiTickHandler = Handler(Looper.getMainLooper())

    private var option: TimerOption? = null
    private var targetElapsedRealtimeMillis: Long? = null
    private var pausedRemainingMillis: Long? = null
    private var alarmActive = false
    private var uiTickRunnable: Runnable? = null

    private val playerListener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (option != CurrentItemTimerOption) return
          when {
            isPlaying && pausedRemainingMillis != null -> resumeAlarm()
            !isPlaying && targetElapsedRealtimeMillis != null -> pauseAlarm()
          }
        }
      }

    @OptIn(UnstableApi::class)
    fun startTimer(
      delayInSeconds: Double,
      option: TimerOption,
    ) {
      stopTimer()

      val totalMillis = (delayInSeconds * 1000).toLong()
      if (totalMillis <= 0L) return

      this.option = option

      exoPlayer.removeListener(playerListener)
      exoPlayer.addListener(playerListener)

      if (option == CurrentItemTimerOption && !exoPlayer.isPlaying) {
        pausedRemainingMillis = totalMillis
        broadcastRemaining(totalMillis / 1000)
        return
      }

      scheduleAlarm(totalMillis)
      startUiTicker()
    }

    fun stopTimer() {
      cancelAlarm()
      stopUiTicker()
      targetElapsedRealtimeMillis = null
      pausedRemainingMillis = null
      option = null
      exoPlayer.removeListener(playerListener)
    }

    @OptIn(UnstableApi::class)
    fun onAlarmFired() {
      stopUiTicker()
      targetElapsedRealtimeMillis = null
      pausedRemainingMillis = null
      option = null
      alarmActive = false
      exoPlayer.removeListener(playerListener)
      PlaybackEvents.emit(PlaybackEvent.TimerExpired)
    }

    private fun scheduleAlarm(remainingMillis: Long) {
      val target = SystemClock.elapsedRealtime() + remainingMillis
      targetElapsedRealtimeMillis = target
      pausedRemainingMillis = null

      val pendingIntent = sleepPendingIntent()
      val canScheduleExact =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

      if (canScheduleExact) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, target, pendingIntent)
      } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, target, pendingIntent)
      }
      alarmActive = true
      broadcastRemaining(remainingMillis / 1000)
    }

    private fun pauseAlarm() {
      val target = targetElapsedRealtimeMillis ?: return
      cancelAlarm()
      val remaining = (target - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
      pausedRemainingMillis = remaining
      targetElapsedRealtimeMillis = null
      stopUiTicker()
      broadcastRemaining(remaining / 1000)
    }

    private fun resumeAlarm() {
      val remaining = pausedRemainingMillis ?: return
      if (remaining <= 0L) {
        onAlarmFired()
        return
      }
      scheduleAlarm(remaining)
      startUiTicker()
    }

    private fun cancelAlarm() {
      if (alarmActive) {
        alarmManager.cancel(sleepPendingIntent())
        alarmActive = false
      }
    }

    private fun startUiTicker() {
      stopUiTicker()
      val runnable =
        object : Runnable {
          override fun run() {
            val target = targetElapsedRealtimeMillis ?: return
            val remainingMillis = (target - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            val remainingSeconds = remainingMillis / 1000
            broadcastRemaining(remainingSeconds)
            if (remainingMillis > 0L) {
              uiTickHandler.postDelayed(this, UI_TICK_INTERVAL_MILLIS)
            }
          }
        }
      uiTickRunnable = runnable
      uiTickHandler.post(runnable)
    }

    private fun stopUiTicker() {
      uiTickRunnable?.let { uiTickHandler.removeCallbacks(it) }
      uiTickRunnable = null
    }

    @OptIn(UnstableApi::class)
    private fun broadcastRemaining(seconds: Long) {
      PlaybackEvents.emit(PlaybackEvent.TimerTick(seconds))
    }

    private fun sleepPendingIntent(): PendingIntent =
      PendingIntent.getBroadcast(
        applicationContext,
        ALARM_REQUEST_CODE,
        Intent(applicationContext, SleepTimerAlarmReceiver::class.java)
          .setAction(ACTION_SLEEP_TIMER_ALARM)
          .setPackage(applicationContext.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    companion object {
      const val ACTION_SLEEP_TIMER_ALARM = "io.github.hobin66.webdavplayer.player.SLEEP_TIMER_ALARM"
      private const val ALARM_REQUEST_CODE = 0x5337
      private const val UI_TICK_INTERVAL_MILLIS = 1_000L
    }
  }
