package org.grakovne.lissen.playback

import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerStopMode
import org.grakovne.lissen.lib.domain.TimerOption

enum class SleepTimerStage {
  IDLE,
  DURATION_COUNTDOWN,
  WAITING_FOR_CURRENT_ITEM_END,
}

enum class SleepTimerExpiryAction {
  PAUSE_NOW,
  SWITCH_TO_CURRENT_ITEM_END,
}

fun initialSleepTimerStage(option: TimerOption?): SleepTimerStage =
  when (option) {
    is DurationTimerOption -> SleepTimerStage.DURATION_COUNTDOWN
    CurrentEpisodeTimerOption -> SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END
    null -> SleepTimerStage.IDLE
  }

fun resolveSleepTimerExpiryAction(
  option: TimerOption?,
  stage: SleepTimerStage,
): SleepTimerExpiryAction =
  when {
    option is DurationTimerOption &&
      option.stopMode == DurationTimerStopMode.AFTER_CURRENT_EPISODE &&
      stage == SleepTimerStage.DURATION_COUNTDOWN -> SleepTimerExpiryAction.SWITCH_TO_CURRENT_ITEM_END

    else -> SleepTimerExpiryAction.PAUSE_NOW
  }

fun shouldAdjustCurrentItemSleepTimer(
  option: TimerOption?,
  stage: SleepTimerStage,
): Boolean =
  when (option) {
    CurrentEpisodeTimerOption -> {
      stage == SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END
    }

    is DurationTimerOption -> {
      option.stopMode == DurationTimerStopMode.AFTER_CURRENT_EPISODE &&
        stage == SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END
    }

    null -> {
      false
    }
  }
