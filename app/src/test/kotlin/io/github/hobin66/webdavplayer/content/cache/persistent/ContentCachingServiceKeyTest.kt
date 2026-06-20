package io.github.hobin66.webdavplayer.content.cache.persistent

import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContentCachingServiceKeyTest {
  @Test
  fun `cache bookkeeping key ignores skip settings`() {
    val original = detailedItem(introSkipSeconds = 0, outroSkipSeconds = 0)
    val updated = detailedItem(introSkipSeconds = 12, outroSkipSeconds = 8)

    assertEquals(contentCachingKey(original), contentCachingKey(updated))
  }

  private fun detailedItem(
    introSkipSeconds: Int,
    outroSkipSeconds: Int,
  ) = DetailedItem(
    id = "book-id",
    title = "Book",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = null,
    libraryId = "webdav_library",
    introSkipSeconds = introSkipSeconds,
    outroSkipSeconds = outroSkipSeconds,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
