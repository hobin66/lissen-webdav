package io.github.hobin66.webdavplayer.playback

import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerStopMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepTimerPolicyTest {
  @Test
  fun `duration timer defaults to immediate stop`() {
    assertEquals(DurationTimerStopMode.IMMEDIATE, DurationTimerOption(duration = 15).stopMode)
  }

  @Test
  fun `combined duration timer switches to current item stage after first expiry`() {
    assertEquals(
      SleepTimerExpiryAction.SWITCH_TO_CURRENT_ITEM_END,
      resolveSleepTimerExpiryAction(
        option = DurationTimerOption(duration = 15, stopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM),
        stage = SleepTimerStage.DURATION_COUNTDOWN,
      ),
    )
  }

  @Test
  fun `combined duration timer pauses after current item stage expires`() {
    assertEquals(
      SleepTimerExpiryAction.PAUSE_NOW,
      resolveSleepTimerExpiryAction(
        option = DurationTimerOption(duration = 15, stopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM),
        stage = SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END,
      ),
    )
  }

  @Test
  fun `current item stage is the only stage that reacts to seek and speed changes`() {
    assertFalse(
      shouldAdjustCurrentItemSleepTimer(
        option = DurationTimerOption(duration = 15, stopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM),
        stage = SleepTimerStage.DURATION_COUNTDOWN,
      ),
    )

    assertTrue(
      shouldAdjustCurrentItemSleepTimer(
        option = DurationTimerOption(duration = 15, stopMode = DurationTimerStopMode.AFTER_CURRENT_ITEM),
        stage = SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END,
      ),
    )

    assertTrue(
      shouldAdjustCurrentItemSleepTimer(
        option = CurrentItemTimerOption,
        stage = SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END,
      ),
    )
  }
}
