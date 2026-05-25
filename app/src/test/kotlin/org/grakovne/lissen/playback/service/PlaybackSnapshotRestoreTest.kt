package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackSnapshotRestoreTest {
  @Test
  fun `prefers snapshot over synthetic WebDAV total progress`() {
    val item = directQueueBook(progress = 403.74)

    val restored =
      resolvePlaybackStartPosition(
        book = item,
        snapshot =
          PlaybackSnapshotRecord(
            bookId = item.id,
            chapterId = "ch-372",
            chapterPosition = 32.74,
            totalPosition = 403.74,
            lastUpdated = 1L,
          ),
      )

    assertEquals(371, restored.index)
    assertEquals(32.74, restored.position)
  }

  @Test
  fun `ignores synthetic WebDAV total progress when no snapshot is available`() {
    val restored = resolvePlaybackStartPosition(book = directQueueBook(progress = 403.74))

    assertEquals(0, restored.index)
    assertEquals(0.0, restored.position)
  }

  @Test
  fun `restores chapter index and position from snapshot`() {
    val chapters =
      listOf(
        chapter("ch-1"),
        chapter("ch-2"),
        chapter("ch-3"),
      )

    val restored =
      resolvePlaybackSnapshotStart(
        chapters = chapters,
        snapshot =
          PlaybackSnapshotRecord(
            bookId = "book",
            chapterId = "ch-2",
            chapterPosition = 42.5,
            totalPosition = 43.5,
            lastUpdated = 1L,
          ),
      )

    assertEquals(1, restored?.chapterIndex)
    assertEquals(42.5, restored?.chapterPosition)
  }

  @Test
  fun `returns null when snapshot chapter is missing`() {
    val restored =
      resolvePlaybackSnapshotStart(
        chapters = listOf(chapter("ch-1")),
        snapshot =
          PlaybackSnapshotRecord(
            bookId = "book",
            chapterId = "missing",
            chapterPosition = 5.0,
            totalPosition = 5.0,
            lastUpdated = 1L,
          ),
      )

    assertNull(restored)
  }

  private fun chapter(id: String) =
    PlayingChapter(
      available = true,
      podcastEpisodeState = null,
      duration = 0.0,
      start = 0.0,
      end = 1.0,
      title = id,
      id = id,
    )

  private fun directQueueBook(progress: Double): DetailedItem {
    val chapters =
      (1..500).map { index ->
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 0.0,
          start = (index - 1).toDouble(),
          end = index.toDouble(),
          title = "Episode $index",
          id = "ch-$index",
        )
      }

    val files =
      chapters.map { chapter ->
        BookFile(
          id = chapter.id,
          name = chapter.title,
          duration = 0.0,
          size = null,
          mimeType = "audio/mpeg",
        )
      }

    return DetailedItem(
      id = "book",
      title = "book",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = files,
      chapters = chapters,
      progress =
        MediaProgress(
          currentTime = progress,
          isFinished = false,
          lastUpdate = 1L,
        ),
      libraryId = "webdav_library",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }
}
