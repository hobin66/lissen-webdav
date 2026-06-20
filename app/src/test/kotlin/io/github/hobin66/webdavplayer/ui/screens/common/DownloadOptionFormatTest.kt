package io.github.hobin66.webdavplayer.ui.screens.common

import io.github.hobin66.webdavplayer.lib.domain.CurrentItemDownloadOption
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadOptionFormatTest {
  @Test
  fun `library type resolves chapter label spec`() {
    assertEquals(
      DownloadLabelSpec(DownloadLabelKey.CURRENT_CHAPTER),
      resolveDownloadLabelSpec(CurrentItemDownloadOption, LibraryType.LIBRARY),
    )
  }

  @Test
  fun `unknown type resolves generic item label spec`() {
    assertEquals(
      DownloadLabelSpec(DownloadLabelKey.CURRENT_ITEM),
      resolveDownloadLabelSpec(CurrentItemDownloadOption, LibraryType.UNKNOWN),
    )
  }
}
