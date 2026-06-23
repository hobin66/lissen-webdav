package io.github.hobin66.webdavplayer.channel.webdav.model

import com.squareup.moshi.Moshi
import io.github.hobin66.webdavplayer.lib.domain.MediaProgress
import io.github.hobin66.webdavplayer.playback.service.PlaybackSnapshotRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebdavBookMetadataTest {
  private val metadataAdapter = Moshi.Builder().build().adapter(WebdavBookMetadata::class.java)

  @Test
  fun `deserialization defaults intro and outro skips to zero when omitted`() {
    val metadata =
      requireNotNull(
        metadataAdapter.fromJson(
          """
          {
            "id": "book-id",
            "title": "Book Title",
            "cover": "cover.jpg"
          }
          """.trimIndent(),
        ),
      )

    assertEquals(0, metadata.introSkipSeconds)
    assertEquals(0, metadata.outroSkipSeconds)
    assertNull(metadata.progress)
    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(0, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `deserialization keeps raw skip values and helper methods clamp them`() {
    val metadata =
      requireNotNull(
        metadataAdapter.fromJson(
          """
          {
            "id": "book-id",
            "title": "Book Title",
            "cover": "cover.jpg",
            "introSkipSeconds": -5,
            "outroSkipSeconds": 75
          }
          """.trimIndent(),
        ),
      )

    assertEquals(-5, metadata.introSkipSeconds)
    assertEquals(75, metadata.outroSkipSeconds)
    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(60, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `defaults intro and outro skips to zero when omitted`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = null,
      )

    assertEquals(0, metadata.introSkipSeconds)
    assertEquals(0, metadata.outroSkipSeconds)
    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(0, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `clamps intro and outro skip values into supported range`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = "cover.jpg",
        introSkipSeconds = -7,
        outroSkipSeconds = 120,
      )

    assertEquals(0, metadata.introSkipSecondsOrDefault())
    assertEquals(60, metadata.outroSkipSecondsOrDefault())
  }

  @Test
  fun `returns default cover when metadata cover is empty`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = null,
      )

    assertEquals("cover.jpg", metadata.coverOrDefault())
  }

  @Test
  fun `normalizes author and description values`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = "cover.jpg",
        author = "  John Doe  ",
        description = "  Story intro  ",
      )

    assertEquals("John Doe", metadata.authorOrNull())
    assertEquals("Story intro", metadata.descriptionOrNull())
  }

  @Test
  fun `returns null for blank author and description`() {
    val metadata =
      WebdavBookMetadata(
        id = "book-id",
        title = "Book Title",
        cover = "cover.jpg",
        author = "   ",
        description = "",
      )

    assertNull(metadata.authorOrNull())
    assertNull(metadata.descriptionOrNull())
  }

  @Test
  fun `deserializes playback progress when present`() {
    val metadata =
      requireNotNull(
        metadataAdapter.fromJson(
          """
          {
            "id": "book-id",
            "title": "Book Title",
            "cover": "cover.jpg",
            "progress": {
              "currentTime": 42.5,
              "isFinished": false,
              "lastUpdate": 1234,
              "chapterId": "chapter-2",
              "chapterTime": 12.5
            }
          }
          """.trimIndent(),
        ),
      )

    val progress = requireNotNull(metadata.progress)
    assertEquals(42.5, progress.currentTime)
    assertEquals(false, progress.isFinished)
    assertEquals(1234L, progress.lastUpdate)
    assertEquals("chapter-2", progress.chapterId)
    assertEquals(12.5, progress.chapterTime)
  }

  @Test
  fun `builds playback progress from newer snapshot`() {
    val progress =
      WebdavPlaybackProgress.from(
        mediaProgress =
          MediaProgress(
            currentTime = 10.0,
            isFinished = false,
            lastUpdate = 100L,
          ),
        snapshot =
          PlaybackSnapshotRecord(
            bookId = "book-id",
            chapterId = "chapter-2",
            chapterPosition = 12.0,
            totalPosition = 42.0,
            lastUpdated = 200L,
          ),
      )

    requireNotNull(progress)
    assertEquals(42.0, progress.currentTime)
    assertEquals(200L, progress.lastUpdate)
    assertEquals("chapter-2", progress.chapterId)
    assertEquals(12.0, progress.chapterTime)
  }

  @Test
  fun `builds playback progress from newer media progress`() {
    val progress =
      WebdavPlaybackProgress.from(
        mediaProgress =
          MediaProgress(
            currentTime = 75.0,
            isFinished = true,
            lastUpdate = 300L,
          ),
        snapshot =
          PlaybackSnapshotRecord(
            bookId = "book-id",
            chapterId = "chapter-2",
            chapterPosition = 12.0,
            totalPosition = 42.0,
            lastUpdated = 200L,
          ),
      )

    requireNotNull(progress)
    assertEquals(75.0, progress.currentTime)
    assertEquals(true, progress.isFinished)
    assertEquals(300L, progress.lastUpdate)
    assertNull(progress.chapterId)
    assertNull(progress.chapterTime)
  }

  @Test
  fun `converts playback progress to snapshot when chapter is present`() {
    val snapshot =
      WebdavPlaybackProgress(
        currentTime = 42.0,
        lastUpdate = 200L,
        chapterId = "chapter-2",
        chapterTime = 12.0,
      ).toPlaybackSnapshot("book-id")

    requireNotNull(snapshot)
    assertEquals("book-id", snapshot.bookId)
    assertEquals("chapter-2", snapshot.chapterId)
    assertEquals(12.0, snapshot.chapterPosition)
    assertEquals(42.0, snapshot.totalPosition)
    assertEquals(200L, snapshot.lastUpdated)
  }
}
