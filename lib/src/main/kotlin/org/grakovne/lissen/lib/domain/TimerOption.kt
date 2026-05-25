package org.grakovne.lissen.lib.domain

import java.io.Serializable

sealed interface TimerOption : Serializable

enum class DurationTimerStopMode : Serializable {
  IMMEDIATE,
  AFTER_CURRENT_EPISODE,
}

data class DurationTimerOption(
  val duration: Int,
  val stopMode: DurationTimerStopMode = DurationTimerStopMode.IMMEDIATE,
) : TimerOption

data object CurrentEpisodeTimerOption : TimerOption
