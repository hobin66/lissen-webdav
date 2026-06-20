package io.github.hobin66.webdavplayer.playback.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PlaybackEvent {
  data object PlaybackReady : PlaybackEvent

  data object TimerExpired : PlaybackEvent

  data class TimerTick(
    val remainingSeconds: Long,
  ) : PlaybackEvent
}

object PlaybackEvents {
  private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
  val events = _events.asSharedFlow()

  fun emit(event: PlaybackEvent) {
    _events.tryEmit(event)
  }
}
