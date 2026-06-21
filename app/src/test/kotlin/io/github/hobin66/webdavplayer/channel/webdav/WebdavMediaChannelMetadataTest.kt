package io.github.hobin66.webdavplayer.channel.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class WebdavMediaChannelMetadataTest {
  @Test
  fun `book metadata file name remains lissen metadata`() {
    assertEquals(".lissen-book.json", WebdavMediaChannel.BOOK_METADATA_FILE_NAME)
  }

  @Test
  fun `legacy metadata file name remains available for fallback`() {
    assertEquals(".webdav-player-book.json", WebdavMediaChannel.LEGACY_BOOK_METADATA_FILE_NAME)
  }

  @Test
  fun `metadata candidates prefer lissen file before legacy fallback`() {
    assertEquals(
      listOf(
        "books/demo/.lissen-book.json",
        "books/demo/.webdav-player-book.json",
      ),
      WebdavMediaChannel.metadataFilePathCandidates("books/demo"),
    )
  }

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
