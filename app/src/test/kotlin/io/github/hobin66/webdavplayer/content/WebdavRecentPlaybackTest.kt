package io.github.hobin66.webdavplayer.content

import io.github.hobin66.webdavplayer.lib.domain.BookFile
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.MediaProgress
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import io.github.hobin66.webdavplayer.lib.domain.RecentBook
import io.github.hobin66.webdavplayer.playback.service.PlaybackSnapshotRecord
import io.github.hobin66.webdavplayer.playback.service.PlaybackSnapshotTrigger
import io.github.hobin66.webdavplayer.playback.service.shouldUpdateRecentPlaybackSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebdavRecentPlaybackTest {
  @Test
  fun `merges recent playback by keeping latest item first and deduplicated`() {
    val existing =
      listOf(
        recentBook(id = "book-1", updatedAt = 100),
        recentBook(id = "book-2", updatedAt = 90),
      )

    val merged =
      mergeRecentPlayback(
        existing = existing,
        latest = recentBook(id = "book-2", updatedAt = 120),
        limit = 10,
      )

    assertEquals(listOf("book-2", "book-1"), merged.map { it.id })
    assertEquals(120L, merged.first().listenedLastUpdate)
  }

  @Test
  fun `does not trim progress when duration is unknown`() {
    assertFalse(shouldTrimProgress(totalDuration = 0.0, progress = 25.0))
  }

  @Test
  fun `creates fallback continue item from playing item and snapshot`() {
    val fallback =
      fallbackRecentPlayback(
        recentBooks = emptyList(),
        playingItem = playingItem(progress = null),
        snapshot =
          PlaybackSnapshotRecord(
            bookId = "book-1",
            chapterId = "ch-2",
            chapterPosition = 12.0,
            totalPosition = 42.0,
            lastUpdated = 321L,
          ),
      )

    assertEquals(1, fallback.size)
    assertEquals("book-1", fallback.single().id)
    assertEquals(321L, fallback.single().listenedLastUpdate)
  }

  @Test
  fun `does not create fallback continue item without snapshot`() {
    val fallback =
      fallbackRecentPlayback(
        recentBooks = emptyList(),
        playingItem = playingItem(progress = null),
        snapshot = null,
      )

    assertNull(fallback.singleOrNull())
  }

  @Test
  fun `periodic snapshots do not refresh recent playback summary`() {
    assertFalse(shouldUpdateRecentPlaybackSummary(PlaybackSnapshotTrigger.PERIODIC))
  }

  @Test
  fun `event snapshots refresh recent playback summary`() {
    assertTrue(shouldUpdateRecentPlaybackSummary(PlaybackSnapshotTrigger.EVENT))
  }

  private fun recentBook(
    id: String,
    updatedAt: Long,
  ) = RecentBook(
    id = id,
    title = id,
    subtitle = null,
    author = null,
    listenedPercentage = null,
    listenedLastUpdate = updatedAt,
  )

  private fun playingItem(
    id: String = "book-1",
    progress: MediaProgress?,
    introSkipSeconds: Int = 0,
    outroSkipSeconds: Int = 0,
  ) = DetailedItem(
    id = id,
    title = "Book 1",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files =
      listOf(
        BookFile(
          id = "ch-1",
          name = "Chapter 1",
          duration = 0.0,
          size = null,
          mimeType = "audio/mpeg",
        ),
      ),
    chapters =
      listOf(
        PlayingChapter(
          available = true,
          duration = 0.0,
          start = 0.0,
          end = 1.0,
          title = "Chapter 1",
          id = "ch-1",
        ),
      ),
    progress = progress,
    libraryId = "webdav_library",
    introSkipSeconds = introSkipSeconds,
    outroSkipSeconds = outroSkipSeconds,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
