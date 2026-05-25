package org.grakovne.lissen.ui.components.slider

import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerStopMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SleepTimerSliderMappingTest {
  @Test
  fun `slider duration preserves selected stop mode`() {
    assertEquals(
      DurationTimerOption(duration = 30, stopMode = DurationTimerStopMode.AFTER_CURRENT_EPISODE),
      timerOptionFromSliderValue(30f, DurationTimerStopMode.AFTER_CURRENT_EPISODE),
    )
  }

  @Test
  fun `slider current item option ignores duration stop mode`() {
    assertEquals(
      CurrentEpisodeTimerOption,
      timerOptionFromSliderValue(-1f, DurationTimerStopMode.AFTER_CURRENT_EPISODE),
    )
  }

  @Test
  fun `slider disabled value clears the timer`() {
    assertNull(timerOptionFromSliderValue(0f, DurationTimerStopMode.IMMEDIATE))
  }
}
