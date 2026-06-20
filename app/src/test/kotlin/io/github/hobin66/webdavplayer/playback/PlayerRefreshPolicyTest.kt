package io.github.hobin66.webdavplayer.playback

import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerRefreshPolicyTest {
  @Test
  fun `does not prepare same book twice without force`() {
    val current = makeDetailedItem()

    assertFalse(
      shouldPreparePlaybackBook(
        currentBook = current,
        nextBook = current,
        forceReload = false,
      ),
    )
  }

  @Test
  fun `allows same book reload when force is requested`() {
    val current = makeDetailedItem()

    assertTrue(
      shouldPreparePlaybackBook(
        currentBook = current,
        nextBook = current,
        forceReload = true,
      ),
    )
  }

  private fun makeDetailedItem() =
    DetailedItem(
      id = "book-1",
      title = "Book",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters =
        listOf(
          PlayingChapter(
            available = true,
            duration = 1.0,
            start = 0.0,
            end = 1.0,
            title = "Chapter",
            id = "chapter-1",
          ),
        ),
      progress = null,
      libraryId = "library",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
