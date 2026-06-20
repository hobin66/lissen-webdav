package io.github.hobin66.webdavplayer.playback.service

import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackNavigationPolicyTest {
  @Test
  fun `chapter media id availability follows chapter index`() {
    val book =
      detailedItem(
        chapters =
          listOf(
            chapter(id = "file-1", available = true),
            chapter(id = "file-2", available = false),
            chapter(id = "file-3", available = true),
          ),
      )

    assertTrue(
      isChapterMediaItemAvailable(
        book = book,
        mediaId = WebdavPlayerMediaSourceFactory.MediaId(book.id, 0).toString(),
      ),
    )
    assertFalse(
      isChapterMediaItemAvailable(
        book = book,
        mediaId = WebdavPlayerMediaSourceFactory.MediaId(book.id, 1).toString(),
      ),
    )
  }

  @Test
  fun `invalid chapter media id is unavailable`() {
    val book = detailedItem(chapters = listOf(chapter(id = "file-1", available = true)))

    assertFalse(isChapterMediaItemAvailable(book = book, mediaId = "file-1"))
    assertFalse(
      isChapterMediaItemAvailable(
        book = book,
        mediaId = WebdavPlayerMediaSourceFactory.MediaId(book.id, 99).toString(),
      ),
    )
  }

  private fun detailedItem(chapters: List<PlayingChapter>) =
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
      chapters = chapters,
      progress = null,
      libraryId = "library",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )

  private fun chapter(
    id: String,
    available: Boolean,
  ) = PlayingChapter(
    available = available,
    duration = 1.0,
    start = 0.0,
    end = 1.0,
    title = id,
    id = id,
  )
}
