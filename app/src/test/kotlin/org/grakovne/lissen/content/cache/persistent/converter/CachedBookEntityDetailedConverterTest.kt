package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedBookEntityDetailedConverterTest {
  private val converter = CachedBookEntityDetailedConverter(MediaProgressEntityConverter())

  @Test
  fun `preserves cached intro and outro skip fields`() {
    val detail =
      converter.apply(
        CachedBookEntity(
          detailedBook =
            BookEntity(
              id = "book-id",
              title = "Book",
              subtitle = null,
              author = null,
              narrator = null,
              year = null,
              abstract = null,
              publisher = null,
              duration = 0,
              libraryId = "webdav_library",
              seriesJson = null,
              seriesNames = null,
              introSkipSeconds = 12,
              outroSkipSeconds = 8,
              createdAt = 0L,
              updatedAt = 0L,
            ),
          files = emptyList(),
          chapters = emptyList(),
          progress = null,
        ),
      )

    assertEquals(12, detail.introSkipSeconds)
    assertEquals(8, detail.outroSkipSeconds)
  }
}
