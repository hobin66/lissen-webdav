package io.github.hobin66.webdavplayer.ui.screens.player.composable

import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerStopMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimerSheetStateTest {
  @Test
  fun `uses active duration timer stop mode when one is selected`() {
    assertEquals(
      DurationTimerStopMode.AFTER_CURRENT_ITEM,
      resolveDurationTimerStopMode(
        currentOption = DurationTimerOption(duration = 15, stopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM),
        preferredStopMode = DurationTimerStopMode.IMMEDIATE,
      ),
    )
  }

  @Test
  fun `falls back to persisted preference when current option is not a duration timer`() {
    assertEquals(
      DurationTimerStopMode.AFTER_CURRENT_ITEM,
      resolveDurationTimerStopMode(
        currentOption = null,
        preferredStopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM,
      ),
    )

    assertEquals(
      DurationTimerStopMode.AFTER_CURRENT_ITEM,
      resolveDurationTimerStopMode(
        currentOption = CurrentItemTimerOption,
        preferredStopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM,
      ),
    )
  }
}
