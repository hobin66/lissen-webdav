package org.grakovne.lissen.channel.webdav.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebdavHrefPathNormalizationTest {
  @Test
  fun `decodes valid percent encoded href path`() {
    assertEquals(
      "/webdav/你好/0001.m4a",
      normalizeWebdavHrefToAbsolutePath("/webdav/%E4%BD%A0%E5%A5%BD/0001.m4a"),
    )
  }

  @Test
  fun `preserves literal percent characters in href path`() {
    assertEquals(
      "/webdav/100%的股份/0001.m4a",
      normalizeWebdavHrefToAbsolutePath("/webdav/100%的股份/0001.m4a"),
    )
  }

  @Test
  fun `decodes valid escapes and preserves invalid percent sequences`() {
    assertEquals(
      "/webdav/你好/%的股份/0001.m4a",
      normalizeWebdavHrefToAbsolutePath("/webdav/%E4%BD%A0%E5%A5%BD/%的股份/0001.m4a"),
    )
  }
}
