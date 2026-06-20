package io.github.hobin66.webdavplayer.lib.domain

import java.io.Serializable

sealed interface TimerOption : Serializable

enum class DurationTimerStopMode : Serializable {
  IMMEDIATE,
  AFTER_CURRENT_ITEM,
}

data class DurationTimerOption(
  val duration: Int,
  val stopMode: DurationTimerStopMode = DurationTimerStopMode.IMMEDIATE,
) : TimerOption

data object CurrentItemTimerOption : TimerOption
