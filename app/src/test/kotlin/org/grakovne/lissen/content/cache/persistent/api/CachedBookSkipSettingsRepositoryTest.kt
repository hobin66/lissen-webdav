package org.grakovne.lissen.content.cache.persistent.api

import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookSkipSettingsDao
import org.grakovne.lissen.content.cache.persistent.entity.BookSkipSettingsEntity
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedBookSkipSettingsRepositoryTest {
  @Test
  fun `seed persisted book skip settings stores incoming defaults when missing locally`() =
    runBlocking {
      val dao = RecordingCachedBookSkipSettingsDao()
      val repository = CachedBookSkipSettingsRepository(dao)
      val incoming = detailedItem(introSkipSeconds = 12, outroSkipSeconds = 8)

      val resolved =
        repository.seedPersistedBookSkipSettingsIfMissing(book = incoming)

      assertEquals(12, resolved.introSkipSeconds)
      assertEquals(8, resolved.outroSkipSeconds)
      assertEquals(BookSkipSettingsEntity(bookId = "book-1", introSkipSeconds = 12, outroSkipSeconds = 8), dao.entity)
    }

  @Test
  fun `seed persisted book skip settings keeps existing local override`() =
    runBlocking {
      val dao =
        RecordingCachedBookSkipSettingsDao(
          entity = BookSkipSettingsEntity(bookId = "book-1", introSkipSeconds = 3, outroSkipSeconds = 5),
        )
      val repository = CachedBookSkipSettingsRepository(dao)
      val incoming = detailedItem(introSkipSeconds = 12, outroSkipSeconds = 8)

      val resolved =
        repository.seedPersistedBookSkipSettingsIfMissing(book = incoming)

      assertEquals(3, resolved.introSkipSeconds)
      assertEquals(5, resolved.outroSkipSeconds)
      assertEquals(BookSkipSettingsEntity(bookId = "book-1", introSkipSeconds = 3, outroSkipSeconds = 5), dao.entity)
    }

  @Test
  fun `apply persisted book skip settings leaves incoming values untouched when nothing was saved locally`() =
    runBlocking {
      val dao = RecordingCachedBookSkipSettingsDao()
      val repository = CachedBookSkipSettingsRepository(dao)
      val incoming = detailedItem(introSkipSeconds = 12, outroSkipSeconds = 8)

      val resolved =
        repository.applyPersistedBookSkipSettings(book = incoming)

      assertEquals(12, resolved.introSkipSeconds)
      assertEquals(8, resolved.outroSkipSeconds)
      assertEquals(null, dao.entity)
    }

  @Test
  fun `apply persisted book skip settings prefers local override when available`() =
    runBlocking {
      val dao =
        RecordingCachedBookSkipSettingsDao(
          entity = BookSkipSettingsEntity(bookId = "book-1", introSkipSeconds = 2, outroSkipSeconds = 4),
        )
      val repository = CachedBookSkipSettingsRepository(dao)
      val incoming = detailedItem(introSkipSeconds = 12, outroSkipSeconds = 8)

      val resolved =
        repository.applyPersistedBookSkipSettings(book = incoming)

      assertEquals(2, resolved.introSkipSeconds)
      assertEquals(4, resolved.outroSkipSeconds)
    }

  @Test
  fun `update persisted book skip settings stores local override independently`() =
    runBlocking {
      val dao = RecordingCachedBookSkipSettingsDao()
      val repository = CachedBookSkipSettingsRepository(dao)

      repository.updateBookSkipSettings(
        bookId = "book-1",
        introSkipSeconds = 15,
        outroSkipSeconds = 7,
      )

      assertEquals(
        BookSkipSettingsEntity(bookId = "book-1", introSkipSeconds = 15, outroSkipSeconds = 7),
        dao.entity,
      )
    }

  private class RecordingCachedBookSkipSettingsDao(
    var entity: BookSkipSettingsEntity? = null,
  ) : CachedBookSkipSettingsDao {
    override suspend fun fetchPersistedBookSkipSettings(bookId: String): BookSkipSettingsEntity? = entity?.takeIf { it.bookId == bookId }

    override suspend fun upsertPersistedBookSkipSettings(settings: BookSkipSettingsEntity) {
      entity = settings
    }
  }

  private fun detailedItem(
    introSkipSeconds: Int,
    outroSkipSeconds: Int,
  ) = DetailedItem(
    id = "book-1",
    title = "Book",
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
          id = "file-1",
          name = "chapter.mp3",
          duration = 1.0,
          size = 1L,
          mimeType = "audio/mpeg",
        ),
      ),
    chapters =
      listOf(
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 1.0,
          start = 0.0,
          end = 1.0,
          title = "Chapter 1",
          id = "file-1",
        ),
      ),
    progress = null,
    libraryId = "webdav_library",
    introSkipSeconds = introSkipSeconds,
    outroSkipSeconds = outroSkipSeconds,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
