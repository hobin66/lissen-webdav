package org.grakovne.lissen.playback.service

import com.squareup.moshi.Types
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.lib.domain.RecentBook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackSnapshotJsonTest {
  @Test
  fun `serializes playback snapshot records through shared moshi instance`() {
    val adapter =
      moshi.adapter<Map<String, PlaybackSnapshotRecord>>(
        Types.newParameterizedType(
          Map::class.java,
          String::class.java,
          PlaybackSnapshotRecord::class.java,
        ),
      )

    val json =
      adapter.toJson(
        mapOf(
          "book" to
            PlaybackSnapshotRecord(
              bookId = "book",
              chapterId = "chapter-372",
              chapterPosition = 12.34,
              totalPosition = 399.23,
              lastUpdated = 1L,
            ),
        ),
      )

    val restored = adapter.fromJson(json)

    assertEquals("chapter-372", restored?.get("book")?.chapterId)
    assertEquals(12.34, restored?.get("book")?.chapterPosition)
  }

  @Test
  fun `serializes recent book list through shared moshi instance`() {
    val adapter =
      moshi.adapter<List<RecentBook>>(
        Types.newParameterizedType(
          List::class.java,
          RecentBook::class.java,
        ),
      )

    val json =
      adapter.toJson(
        listOf(
          RecentBook(
            id = "book",
            title = "title",
            subtitle = null,
            author = null,
            listenedPercentage = null,
            listenedLastUpdate = 1L,
          ),
        ),
      )

    val restored = adapter.fromJson(json)

    assertEquals("book", restored?.singleOrNull()?.id)
    assertEquals(1L, restored?.singleOrNull()?.listenedLastUpdate)
  }
}
