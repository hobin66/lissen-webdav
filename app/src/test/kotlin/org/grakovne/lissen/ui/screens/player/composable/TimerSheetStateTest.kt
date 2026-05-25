package org.grakovne.lissen.ui.screens.player.composable

import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerStopMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimerSheetStateTest {
  @Test
  fun `uses active duration timer stop mode when one is selected`() {
    assertEquals(
      DurationTimerStopMode.AFTER_CURRENT_EPISODE,
      resolveDurationTimerStopMode(
        currentOption = DurationTimerOption(duration = 15, stopMode = DurationTimerStopMode.AFTER_CURRENT_EPISODE),
        preferredStopMode = DurationTimerStopMode.IMMEDIATE,
      ),
    )
  }

  @Test
  fun `falls back to persisted preference when current option is not a duration timer`() {
    assertEquals(
      DurationTimerStopMode.AFTER_CURRENT_EPISODE,
      resolveDurationTimerStopMode(
        currentOption = null,
        preferredStopMode = DurationTimerStopMode.AFTER_CURRENT_EPISODE,
      ),
    )

    assertEquals(
      DurationTimerStopMode.AFTER_CURRENT_EPISODE,
      resolveDurationTimerStopMode(
        currentOption = CurrentEpisodeTimerOption,
        preferredStopMode = DurationTimerStopMode.AFTER_CURRENT_EPISODE,
      ),
    )
  }
}
