package io.github.hobin66.webdavplayer.ui.screens.player.composable

import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.ui.screens.player.composable.common.NowPlayingTitleKey
import io.github.hobin66.webdavplayer.ui.screens.player.composable.common.resolveNowPlayingTitleKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProvideNowPlayingTitleTest {
  @Test
  fun `library title resolves to library key`() {
    assertEquals(
      NowPlayingTitleKey.LIBRARY,
      resolveNowPlayingTitleKey(LibraryType.LIBRARY),
    )
  }

  @Test
  fun `unknown title resolves to generic key`() {
    assertEquals(
      NowPlayingTitleKey.GENERIC,
      resolveNowPlayingTitleKey(LibraryType.UNKNOWN),
    )
  }
}
