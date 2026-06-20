package io.github.hobin66.webdavplayer.channel.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class WebdavMediaChannelMetadataTest {
  @Test
  fun `default metadata id stays stable for same directory`() {
    val first = WebdavMediaChannel.defaultWebdavMetadataId("books/the-hobbit")
    val second = WebdavMediaChannel.defaultWebdavMetadataId("books/the-hobbit")

    assertEquals(first, second)
  }

  @Test
  fun `default metadata id changes for different directories`() {
    val first = WebdavMediaChannel.defaultWebdavMetadataId("books/the-hobbit")
    val second = WebdavMediaChannel.defaultWebdavMetadataId("books/dune")

    assertNotEquals(first, second)
  }
}
