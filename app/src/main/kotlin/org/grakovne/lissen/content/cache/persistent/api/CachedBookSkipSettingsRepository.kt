package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.content.cache.persistent.dao.CachedBookSkipSettingsDao
import org.grakovne.lissen.content.cache.persistent.entity.BookSkipSettingsEntity
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.playback.BookSkipSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookSkipSettingsRepository
  @Inject
  constructor(
    private val dao: CachedBookSkipSettingsDao,
  ) {
    suspend fun fetchPersistedBookSkipSettings(bookId: String): BookSkipSettings? =
      dao
        .fetchPersistedBookSkipSettings(bookId)
        ?.toBookSkipSettings()

    suspend fun seedPersistedBookSkipSettingsIfMissing(book: DetailedItem): DetailedItem {
      val persisted = fetchPersistedBookSkipSettings(book.id)
      if (persisted != null) {
        return book.withPersistedBookSkipSettings(persisted)
      }

      val seeded =
        BookSkipSettings(
          introSkipSeconds = book.introSkipSeconds,
          outroSkipSeconds = book.outroSkipSeconds,
        )

      updateBookSkipSettings(
        bookId = book.id,
        introSkipSeconds = seeded.normalizedIntroSkipSeconds,
        outroSkipSeconds = seeded.normalizedOutroSkipSeconds,
      )

      return book.withPersistedBookSkipSettings(seeded)
    }

    suspend fun applyPersistedBookSkipSettings(book: DetailedItem): DetailedItem =
      fetchPersistedBookSkipSettings(book.id)
        ?.let { book.withPersistedBookSkipSettings(it) }
        ?: book

    suspend fun updateBookSkipSettings(
      bookId: String,
      introSkipSeconds: Int,
      outroSkipSeconds: Int,
    ) {
      val settings =
        BookSkipSettings(
          introSkipSeconds = introSkipSeconds,
          outroSkipSeconds = outroSkipSeconds,
        )

      dao.upsertPersistedBookSkipSettings(
        BookSkipSettingsEntity(
          bookId = bookId,
          introSkipSeconds = settings.normalizedIntroSkipSeconds,
          outroSkipSeconds = settings.normalizedOutroSkipSeconds,
        ),
      )
    }
  }

internal fun DetailedItem.withPersistedBookSkipSettings(settings: BookSkipSettings): DetailedItem =
  copy(
    introSkipSeconds = settings.normalizedIntroSkipSeconds,
    outroSkipSeconds = settings.normalizedOutroSkipSeconds,
  )

private fun BookSkipSettingsEntity.toBookSkipSettings(): BookSkipSettings =
  BookSkipSettings(
    introSkipSeconds = introSkipSeconds,
    outroSkipSeconds = outroSkipSeconds,
  )
