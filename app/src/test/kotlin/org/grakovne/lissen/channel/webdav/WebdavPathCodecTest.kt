package org.grakovne.lissen.channel.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WebdavPathCodecTest {
  @Test
  fun `encodes and decodes path losslessly`() {
    val path = "Books/火灶坊/0002 火灶坊.m4a"
    val encoded = WebdavPathCodec.encode(path)

    val decoded = WebdavPathCodec.decode(encoded)
    assertNotNull(decoded)
    assertEquals(path, decoded)
  }
}
