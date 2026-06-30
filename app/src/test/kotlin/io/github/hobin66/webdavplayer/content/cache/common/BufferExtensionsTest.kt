package io.github.hobin66.webdavplayer.content.cache.common

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BufferExtensionsTest {
  @TempDir
  lateinit var tempDir: File

  @Test
  fun `writeToFile replaces target through temporary sibling`() {
    val target = tempDir.resolve("cover.img")
    target.writeText("old")

    Buffer().writeUtf8("new").writeToFile(target)

    assertEquals("new", target.readText())
    assertFalse(target.temporarySibling().exists())
  }
}
