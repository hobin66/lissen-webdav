package io.github.hobin66.webdavplayer.content.cache.persistent.converter

import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CachedBookEntityRecentConverterTest {
  private val converter = CachedBookEntityRecentConverter()

  @Test
  fun `returns null percentage when duration is unknown`() {
    val recent =
      converter.apply(
        entity = bookEntity(duration = 0),
        currentTime = 123L to 30.0,
      )

    assertNull(recent.listenedPercentage)
    assertEquals(123L, recent.listenedLastUpdate)
  }

  @Test
  fun `returns percentage when duration is known`() {
    val recent =
      converter.apply(
        entity = bookEntity(duration = 120),
        currentTime = 456L to 30.0,
      )

    assertEquals(25, recent.listenedPercentage)
    assertEquals(456L, recent.listenedLastUpdate)
  }

  private fun bookEntity(duration: Int) =
    BookEntity(
      id = "book-id",
      title = "Book",
      subtitle = null,
      author = null,
      narrator = null,
      year = null,
      abstract = null,
      publisher = null,
      duration = duration,
      libraryId = "webdav_library",
      seriesJson = null,
      seriesNames = null,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
