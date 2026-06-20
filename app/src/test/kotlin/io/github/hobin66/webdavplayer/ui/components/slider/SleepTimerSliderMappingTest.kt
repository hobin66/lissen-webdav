package io.github.hobin66.webdavplayer.ui.components.slider

import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerStopMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SleepTimerSliderMappingTest {
  @Test
  fun `slider duration preserves selected stop mode`() {
    assertEquals(
      DurationTimerOption(duration = 30, stopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM),
      timerOptionFromSliderValue(30f, DurationTimerStopMode.AFTER_CURRENT_ITEM),
    )
  }

  @Test
  fun `slider current item option ignores duration stop mode`() {
    assertEquals(
      CurrentItemTimerOption,
      timerOptionFromSliderValue(-1f, DurationTimerStopMode.AFTER_CURRENT_ITEM),
    )
  }

  @Test
  fun `slider disabled value clears the timer`() {
    assertNull(timerOptionFromSliderValue(0f, DurationTimerStopMode.IMMEDIATE))
  }
}
