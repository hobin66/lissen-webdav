package org.grakovne.lissen.content.cache.persistent.dao

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.entity.BookChapterEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookFileEntity
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.grakovne.lissen.content.cache.persistent.withRuntimeBookSkipSettings
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.playback.BookSkipSettings
import org.grakovne.lissen.playback.BookSkipSettingsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedBookDaoSkipUpdateTest {
  @Test
  fun `updates only cached skip fields for existing book`() =
    runBlocking {
      val original =
        BookEntity(
          id = "book-id",
          title = "Cached Book",
          subtitle = "Subtitle",
          author = "Author",
          narrator = "Narrator",
          year = "2026",
          abstract = "Abstract",
          publisher = "Publisher",
          duration = 123,
          libraryId = "webdav_library",
          seriesJson = null,
          seriesNames = null,
          createdAt = 10L,
          updatedAt = 20L,
          introSkipSeconds = 1,
          outroSkipSeconds = 2,
        )
      val dao = RecordingCachedBookDao(original)

      val updatedRows =
        dao.updateBookSkipSettings(
          bookId = "book-id",
          introSkipSeconds = 12,
          outroSkipSeconds = 8,
        )

      val updated = dao.fetchBook("book-id")
      assertEquals(1, updatedRows)
      assertEquals(original.copy(introSkipSeconds = 12, outroSkipSeconds = 8), updated)
    }

  @Test
  fun `does not insert uncached book when updating skip fields`() =
    runBlocking {
      val dao = RecordingCachedBookDao(book = null)

      val updatedRows =
        dao.updateBookSkipSettings(
          bookId = "missing-book-id",
          introSkipSeconds = 12,
          outroSkipSeconds = 8,
        )

      assertEquals(0, updatedRows)
      assertEquals(null, dao.fetchBook("missing-book-id"))
    }

  @Test
  fun `cache metadata upsert accepts authoritative incoming skip fields for existing book`() =
    runBlocking {
      val saved =
        BookEntity(
          id = "book-id",
          title = "Cached Book",
          subtitle = "Subtitle",
          author = "Author",
          narrator = "Narrator",
          year = "2026",
          abstract = "Abstract",
          publisher = "Publisher",
          duration = 123,
          libraryId = "webdav_library",
          seriesJson = null,
          seriesNames = null,
          createdAt = 10L,
          updatedAt = 20L,
          introSkipSeconds = 0,
          outroSkipSeconds = 0,
        )
      val dao = RecordingCachedBookDao(saved)

      dao.upsertCachedBook(
        book =
          detailedItem(
            title = "Refreshed Metadata",
            introSkipSeconds = 15,
            outroSkipSeconds = 5,
          ),
        fetchedChapters = emptyList(),
        droppedChapters = emptyList(),
      )

      val updated = dao.fetchBook("book-id")
      assertEquals("Refreshed Metadata", updated?.title)
      assertEquals(15, updated?.introSkipSeconds)
      assertEquals(5, updated?.outroSkipSeconds)
    }

  @Test
  fun `runtime skip settings override incoming metadata before cache upsert`() {
    BookSkipSettingsStore.clear()
    try {
      BookSkipSettingsStore.put(
        "book-id",
        BookSkipSettings(introSkipSeconds = 15, outroSkipSeconds = 5),
      )

      val merged =
        detailedItem(
          title = "Stale Cache Task Metadata",
          introSkipSeconds = 0,
          outroSkipSeconds = 0,
        ).withRuntimeBookSkipSettings()

      assertEquals(15, merged.introSkipSeconds)
      assertEquals(5, merged.outroSkipSeconds)
    } finally {
      BookSkipSettingsStore.clear()
    }
  }

  private class RecordingCachedBookDao(
    private var book: BookEntity?,
  ) : CachedBookDao {
    private var files: List<BookFileEntity> = emptyList()
    private var chapters: List<BookChapterEntity> = emptyList()

    override suspend fun fetchCachedBooks(query: SupportSQLiteQuery): List<BookEntity> = unsupported()

    override suspend fun countCachedBooks(libraryId: String?): Int = unsupported()

    override suspend fun searchBooks(query: SupportSQLiteQuery): List<BookEntity> = unsupported()

    override suspend fun fetchRecentlyListenedCachedBooks(libraryId: String?): List<BookEntity> = unsupported()

    override suspend fun fetchCachedBook(bookId: String): CachedBookEntity? =
      book
        ?.takeIf { it.id == bookId }
        ?.let {
          CachedBookEntity(
            detailedBook = it,
            files = files,
            chapters = chapters,
            progress = null,
          )
        }

    override fun isBookCached(bookId: String): LiveData<Boolean> = MutableLiveData(book?.id == bookId)

    override suspend fun fetchCachedItems(
      pageSize: Int,
      pageNumber: Int,
    ): List<CachedBookEntity> = unsupported()

    override suspend fun fetchCachedItems(): List<CachedBookEntity> = unsupported()

    override suspend fun fetchCachedItemsCount(): Int = unsupported()

    override fun isBookChapterCached(
      bookId: String,
      chapterId: String,
    ): LiveData<Boolean> = MutableLiveData(false)

    override suspend fun fetchLatestUpdate(libraryId: String): Long? = unsupported()

    override suspend fun fetchBook(bookId: String): BookEntity? = book?.takeIf { it.id == bookId }

    override suspend fun upsertBook(book: BookEntity) {
      this.book = book
    }

    override suspend fun upsertBookFiles(files: List<BookFileEntity>) {
      this.files = files
    }

    override suspend fun upsertBookChapters(chapters: List<BookChapterEntity>) {
      this.chapters = chapters
    }

    override suspend fun upsertMediaProgress(progress: MediaProgressEntity) = unsupported()

    override suspend fun fetchMediaProgress(bookId: String): MediaProgressEntity? = unsupported()

    override suspend fun deleteBook(book: BookEntity) = unsupported()

    override suspend fun deleteMediaProgress(bookId: String) = unsupported()

    override suspend fun updateBookSkipSettings(
      bookId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ): Int {
      val existing = book?.takeIf { it.id == bookId } ?: return 0
      book =
        existing.copy(
          introSkipSeconds = introSkipSeconds,
          outroSkipSeconds = outroSkipSeconds,
        )
      return 1
    }

    private fun unsupported(): Nothing = error("Not used by this focused test")
  }

  private fun detailedItem(
    title: String,
    introSkipSeconds: Int,
    outroSkipSeconds: Int,
  ) = DetailedItem(
    id = "book-id",
    title = title,
    subtitle = "Subtitle",
    author = "Author",
    narrator = "Narrator",
    publisher = "Publisher",
    series = emptyList(),
    year = "2026",
    abstract = "Abstract",
    files =
      listOf(
        BookFile(
          id = "chapter-id",
          name = "chapter.mp3",
          duration = 123.0,
          size = 100L,
          mimeType = "audio/mpeg",
        ),
      ),
    chapters =
      listOf(
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 123.0,
          start = 0.0,
          end = 123.0,
          title = "Chapter",
          id = "chapter-id",
        ),
      ),
    progress = null,
    libraryId = "webdav_library",
    introSkipSeconds = introSkipSeconds,
    outroSkipSeconds = outroSkipSeconds,
    localProvided = false,
    createdAt = 10L,
    updatedAt = 30L,
  )
}
