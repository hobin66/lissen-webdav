package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookDetailCache
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebdavCacheValidationTest {
  @Test
  fun `reuses cached detail when etag matches`() {
    val cache = webdavBookDetailCache(directoryEtag = "\"abc\"", directoryLastModified = "old")

    assertTrue(
      shouldUseCachedWebdavDetail(
        cache = cache,
        directoryEtag = "\"abc\"",
        directoryLastModified = "newer",
      ),
    )
  }

  @Test
  fun `reuses cached detail when last modified matches and etag is absent`() {
    val cache = webdavBookDetailCache(directoryEtag = null, directoryLastModified = "Mon, 01 Jan 2024 00:00:00 GMT")

    assertTrue(
      shouldUseCachedWebdavDetail(
        cache = cache,
        directoryEtag = null,
        directoryLastModified = "Mon, 01 Jan 2024 00:00:00 GMT",
      ),
    )
  }

  @Test
  fun `does not reuse cached detail when validation differs`() {
    val cache = webdavBookDetailCache(directoryEtag = "\"abc\"", directoryLastModified = "old")

    assertFalse(
      shouldUseCachedWebdavDetail(
        cache = cache,
        directoryEtag = "\"def\"",
        directoryLastModified = "old",
      ),
    )
  }

  private fun webdavBookDetailCache(
    directoryEtag: String?,
    directoryLastModified: String?,
  ) = WebdavBookDetailCache(
    bookId = "book-id",
    directoryEtag = directoryEtag,
    directoryLastModified = directoryLastModified,
    item =
      DetailedItem(
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
        localProvided = false,
        createdAt = 0L,
        updatedAt = 0L,
      ),
  )
}
