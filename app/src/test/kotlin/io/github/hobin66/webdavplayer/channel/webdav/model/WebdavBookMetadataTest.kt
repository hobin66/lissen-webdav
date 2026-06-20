package io.github.hobin66.webdavplayer.channel.webdav.model

import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebdavBookMetadataTest {
  private val metadataAdapter = Moshi.Builder().build().adapter(WebdavBookMetadata::class.java)

  @Test
  fun `deserialization defaults intro and outro skips to zero when omitted`() {
    val metadata =
      requireNotNull(
        metadataAdapter.fromJson(
          """
          {
            "id": "book-id",
            "title": "Book Title",
            "cover": "cover.jpg"
          }
          """.trimIndent(),
        ),
      )

    assertEquals(0, metadata.introSkipSeconds)
    assertEquals(0, metadata.outroSkipSeconds)
    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(0, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `deserialization keeps raw skip values and helper methods clamp them`() {
    val metadata =
      requireNotNull(
        metadataAdapter.fromJson(
          """
          {
            "id": "book-id",
            "title": "Book Title",
            "cover": "cover.jpg",
            "introSkipSeconds": -5,
            "outroSkipSeconds": 75
          }
          """.trimIndent(),
        ),
      )

    assertEquals(-5, metadata.introSkipSeconds)
    assertEquals(75, metadata.outroSkipSeconds)
    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(60, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `defaults intro and outro skips to zero when omitted`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = null,
      )

    assertEquals(0, metadata.introSkipSeconds)
    assertEquals(0, metadata.outroSkipSeconds)
    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(0, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `clamps intro and outro skip values into supported range`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = "cover.jpg",
        introSkipSeconds = -7,
        outroSkipSeconds = 120,
      )

    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(60, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `returns default cover when metadata cover is empty`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = null,
      )

    assertEquals("cover.jpg", metadata.coverOrDefault())
  }

  @Test
  fun `normalizes author and description values`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = "cover.jpg",
        author = "  John Doe  ",
        description = "  Story intro  ",
      )

    assertEquals("John Doe", metadata.authorOrNull())
    assertEquals("Story intro", metadata.descriptionOrNull())
  }

  @Test
  fun `returns null for blank author and description`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = "cover.jpg",
        author = "   ",
        description = "",
      )

    assertNull(metadata.authorOrNull())
    assertNull(metadata.descriptionOrNull())
  }
}
