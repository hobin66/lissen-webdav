package io.github.hobin66.webdavplayer.ui.screens.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryRefreshReloadPolicyTest {
  @Test
  fun `forces placeholder when a remote refresh was just completed`() {
    assertTrue(
      shouldForceLibraryReloadAfterRemoteRefresh(
        remoteRefreshPending = true,
        pullRefreshing = false,
      ),
    )
  }

  @Test
  fun `does not force placeholder when no remote refresh completion is pending`() {
    assertFalse(
      shouldForceLibraryReloadAfterRemoteRefresh(
        remoteRefreshPending = false,
        pullRefreshing = false,
      ),
    )
  }
}
