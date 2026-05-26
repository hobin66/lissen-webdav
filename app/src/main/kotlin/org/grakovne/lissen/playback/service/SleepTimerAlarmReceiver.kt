package org.grakovne.lissen.playback.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SleepTimerAlarmReceiver : BroadcastReceiver() {
  @Inject
  lateinit var playbackTimer: PlaybackTimer

  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    if (intent.action != PlaybackTimer.ACTION_SLEEP_TIMER_ALARM) return
    playbackTimer.onAlarmFired()
  }
}
