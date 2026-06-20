package io.github.hobin66.webdavplayer.channel.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebdavFileIdResolverTest {
  @Test
  fun `resolves encoded file id`() {
    val relativePath = "Books/Book A/0001 Intro.m4a"
    val encoded = WebdavPathCodec.encode(relativePath)

    assertEquals(relativePath, resolveWebdavFileRelativePath(encoded))
  }

  @Test
  fun `falls back to raw relative path for legacy cache`() {
    val relativePath = "Books/Book A/0001 Intro.m4a"

    assertEquals(relativePath, resolveWebdavFileRelativePath(relativePath))
  }

  @Test
  fun `falls back to raw filename when it looks like audio`() {
    val fileName = "0001 Intro.m4a"

    assertEquals(fileName, resolveWebdavFileRelativePath(fileName))
  }

  @Test
  fun `rejects non-path opaque id`() {
    assertNull(resolveWebdavFileRelativePath("chapter-1"))
  }
}
