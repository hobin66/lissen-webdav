package org.grakovne.lissen.content.cache.common

import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FindRelatedFilesTest {
  @Test
  fun `maps unresolved webdav chapter by id`() {
    val chapter =
      PlayingChapter(
        available = true,
        podcastEpisodeState = null,
        duration = 0.0,
        start = 0.0,
        end = 1.0,
        title = "Chapter 1",
        id = "encoded-file-1",
      )
    val files =
      listOf(
        BookFile(
          id = "encoded-file-1",
          name = "0001.m4a",
          duration = 0.0,
          size = 1024L,
          mimeType = "audio/mp4",
        ),
      )

    val related = findRelatedFiles(chapter, files)

    assertEquals(listOf("encoded-file-1"), related.map { it.id })
  }

  @Test
  fun `falls back to timeline overlap when ids differ`() {
    val chapter =
      PlayingChapter(
        available = true,
        podcastEpisodeState = null,
        duration = 10.0,
        start = 10.0,
        end = 20.0,
        title = "Chapter 2",
        id = "chapter-2",
      )
    val files =
      listOf(
        BookFile(
          id = "file-1",
          name = "part-1.mp3",
          duration = 10.0,
          size = 1000L,
          mimeType = "audio/mpeg",
        ),
        BookFile(
          id = "file-2",
          name = "part-2.mp3",
          duration = 10.0,
          size = 1000L,
          mimeType = "audio/mpeg",
        ),
        BookFile(
          id = "file-3",
          name = "part-3.mp3",
          duration = 10.0,
          size = 1000L,
          mimeType = "audio/mpeg",
        ),
      )

    val related = findRelatedFiles(chapter, files)

    assertEquals(listOf("file-2"), related.map { it.id })
  }
}
