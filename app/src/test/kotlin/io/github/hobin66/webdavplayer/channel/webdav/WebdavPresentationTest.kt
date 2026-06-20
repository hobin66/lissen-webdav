package io.github.hobin66.webdavplayer.channel.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebdavPresentationTest {
  @Test
  fun `builds root relative path for nested file`() {
    val result =
      toWebdavRootRelativePath(
        absolutePath = "/webdav/Book A/0001 Intro.m4a",
        rootAbsolutePath = "/webdav",
      )

    assertEquals("Book A/0001 Intro.m4a", result)
  }

  @Test
  fun `returns null when file is outside configured root`() {
    val result =
      toWebdavRootRelativePath(
        absolutePath = "/other/Book A/0001 Intro.m4a",
        rootAbsolutePath = "/webdav",
      )

    assertNull(result)
  }

  @Test
  fun `keeps numeric prefix in displayed title`() {
    assertEquals("0001 Intro", buildTrackDisplayTitle("0001 Intro.m4a"))
  }
}
