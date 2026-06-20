package io.github.hobin66.webdavplayer.ui.screens.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerBackNavigationPolicyTest {
  @Test
  fun `dismisses search before leaving player`() {
    assertEquals(
      PlayerBackAction.DISMISS_SEARCH,
      resolvePlayerBackAction(
        searchRequested = true,
        playingQueueExpanded = true,
        canGoBack = true,
      ),
    )
  }

  @Test
  fun `collapses queue before leaving player`() {
    assertEquals(
      PlayerBackAction.COLLAPSE_QUEUE,
      resolvePlayerBackAction(
        searchRequested = false,
        playingQueueExpanded = true,
        canGoBack = true,
      ),
    )
  }

  @Test
  fun `pops back stack when library screen is already underneath player`() {
    assertEquals(
      PlayerBackAction.GO_BACK,
      resolvePlayerBackAction(
        searchRequested = false,
        playingQueueExpanded = false,
        canGoBack = true,
      ),
    )
  }

  @Test
  fun `shows library when player was opened without a previous destination`() {
    assertEquals(
      PlayerBackAction.SHOW_LIBRARY,
      resolvePlayerBackAction(
        searchRequested = false,
        playingQueueExpanded = false,
        canGoBack = false,
      ),
    )
  }
}
