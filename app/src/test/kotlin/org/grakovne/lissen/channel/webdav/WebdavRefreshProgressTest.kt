package org.grakovne.lissen.channel.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebdavRefreshProgressTest {
  @Test
  fun `starts at zero processed`() {
    assertEquals(
      WebdavRefreshProgress(processedBooks = 0, totalBooks = 8),
      WebdavRefreshProgress.start(totalBooks = 8),
    )
  }

  @Test
  fun `advances processed count without changing total`() {
    assertEquals(
      WebdavRefreshProgress(processedBooks = 3, totalBooks = 8),
      WebdavRefreshProgress
        .start(totalBooks = 8)
        .advance()
        .advance()
        .advance(),
    )
  }

  @Test
  fun `progress ratio uses processed over total`() {
    assertEquals(
      0.5f,
      WebdavRefreshProgress(processedBooks = 4, totalBooks = 8).ratio,
    )
  }
}
