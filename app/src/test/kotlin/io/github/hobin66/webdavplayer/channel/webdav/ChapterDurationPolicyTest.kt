package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.lib.domain.BookFile
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterDurationPolicyTest {
  @Test
  fun `unresolved durations need resolution`() {
    val item = sampleBook(fileDurations = listOf(0.0, 0.0), chapterDurations = listOf(0.0, 0.0))
    assertTrue(needsChapterDurationResolution(item))
  }

  @Test
  fun `resolved durations do not need resolution`() {
    val item = sampleBook(fileDurations = listOf(12.0, 30.5), chapterDurations = listOf(12.0, 30.5))
    assertFalse(needsChapterDurationResolution(item))
  }

  @Test
  fun `applyResolvedFileDurations rebuilds direct queue timeline`() {
    val item = sampleBook(fileDurations = listOf(0.0, 0.0), chapterDurations = listOf(0.0, 0.0))

    val enriched =
      applyResolvedFileDurations(
        item = item,
        durationsByFileId =
          mapOf(
            "file-0" to 10.0,
            "file-1" to 20.0,
          ),
      )

    assertEquals(10.0, enriched.files[0].duration)
    assertEquals(20.0, enriched.files[1].duration)

    assertEquals(10.0, enriched.chapters[0].duration)
    assertEquals(0.0, enriched.chapters[0].start)
    assertEquals(10.0, enriched.chapters[0].end)

    assertEquals(20.0, enriched.chapters[1].duration)
    assertEquals(10.0, enriched.chapters[1].start)
    assertEquals(30.0, enriched.chapters[1].end)
  }

  @Test
  fun `partial resolution keeps unresolved placeholders in timeline`() {
    val item = sampleBook(fileDurations = listOf(0.0, 0.0), chapterDurations = listOf(0.0, 0.0))

    val enriched =
      applyResolvedFileDurations(
        item = item,
        durationsByFileId = mapOf("file-0" to 15.0),
      )

    assertEquals(15.0, enriched.files[0].duration)
    assertEquals(0.0, enriched.files[1].duration)

    assertEquals(15.0, enriched.chapters[0].end)
    assertEquals(15.0, enriched.chapters[1].start)
    assertEquals(15.0 + UNRESOLVED_TIMELINE_DURATION_SECONDS, enriched.chapters[1].end)
    assertEquals(UNRESOLVED_DISPLAY_DURATION_SECONDS, enriched.chapters[1].duration)
  }


  @Test
  fun `direct queue total position uses resolved prefix durations`() {
    val item =
      sampleBook(
        fileDurations = listOf(10.0, 20.0, 0.0),
        chapterDurations = listOf(10.0, 20.0, 0.0),
      )

    val total =
      resolveDirectQueueTotalPositionSeconds(
        chapters = item.chapters,
        currentIndex = 2,
        currentPositionSeconds = 5.0,
      )

    assertEquals(35.0, total)
  }

  @Test
  fun `direct queue total position is unknown when prefix is unresolved`() {
    val item = sampleBook(fileDurations = listOf(0.0, 0.0), chapterDurations = listOf(0.0, 0.0))

    val total =
      resolveDirectQueueTotalPositionSeconds(
        chapters = item.chapters,
        currentIndex = 1,
        currentPositionSeconds = 3.5,
      )

    assertNull(total)
  }

  @Test
  fun `first direct queue chapter does not require a resolved prefix`() {
    val item = sampleBook(fileDurations = listOf(0.0, 0.0), chapterDurations = listOf(0.0, 0.0))

    val total =
      resolveDirectQueueTotalPositionSeconds(
        chapters = item.chapters,
        currentIndex = 0,
        currentPositionSeconds = 3.5,
      )

    assertEquals(3.5, total)
  }

  @Test
  fun `mergeDurationMaps prefers newly resolved values`() {
    val merged =
      mergeDurationMaps(
        existing = mapOf("a" to 1.0),
        incoming = mapOf("a" to 2.5, "b" to 0.0, "c" to 9.0),
      )

    assertEquals(mapOf("a" to 2.5, "c" to 9.0), merged)
  }

  private fun sampleBook(
    fileDurations: List<Double>,
    chapterDurations: List<Double>,
  ): DetailedItem {
    var start = 0.0
    val files =
      fileDurations.mapIndexed { index, duration ->
        BookFile(
          id = "file-$index",
          name = "Track $index",
          duration = duration,
          size = null,
          mimeType = "audio/mpeg",
        )
      }
    val chapters =
      chapterDurations.mapIndexed { index, duration ->
        val timeline = if (duration > 0.0) duration else UNRESOLVED_TIMELINE_DURATION_SECONDS
        val chapter =
          PlayingChapter(
            available = true,
            duration = duration,
            start = start,
            end = start + timeline,
            title = "Chapter $index",
            id = "file-$index",
          )
        start += timeline
        chapter
      }

    return DetailedItem(
      id = "book",
      title = "Book",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = files,
      chapters = chapters,
      progress = null,
      libraryId = "webdav_library",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }
}
