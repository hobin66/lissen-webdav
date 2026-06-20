package io.github.hobin66.webdavplayer.content.cache.persistent.converter

import com.squareup.moshi.Types
import io.github.hobin66.webdavplayer.common.moshi
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookSeriesDto
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.CachedBookEntity
import io.github.hobin66.webdavplayer.lib.domain.BookFile
import io.github.hobin66.webdavplayer.lib.domain.BookSeries
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.PlayingChapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityDetailedConverter
  @Inject
  constructor(
    private val mediaProgressEntityConverter: MediaProgressEntityConverter,
  ) {
    fun apply(entity: CachedBookEntity): DetailedItem =
      DetailedItem(
        id = entity.detailedBook.id,
        title = entity.detailedBook.title,
        subtitle = entity.detailedBook.subtitle,
        author = entity.detailedBook.author,
        narrator = entity.detailedBook.narrator,
        libraryId = entity.detailedBook.libraryId,
        localProvided = true,
        files =
          entity.files.map { fileEntity ->
            BookFile(
              id = fileEntity.bookFileId,
              name = fileEntity.name,
              size = fileEntity.size,
              duration = fileEntity.duration,
              mimeType = fileEntity.mimeType,
            )
          },
        chapters =
          entity.chapters.map { chapterEntity ->
            PlayingChapter(
              duration = chapterEntity.duration,
              start = chapterEntity.start,
              end = chapterEntity.end,
              title = chapterEntity.title,
              available = chapterEntity.isCached,
              id = chapterEntity.bookChapterId,
            )
          },
        abstract = entity.detailedBook.abstract,
        publisher = entity.detailedBook.publisher,
        year = entity.detailedBook.year,
        introSkipSeconds = entity.detailedBook.introSkipSeconds,
        outroSkipSeconds = entity.detailedBook.outroSkipSeconds,
        createdAt = entity.detailedBook.createdAt,
        updatedAt = entity.detailedBook.updatedAt,
        series =
          entity
            .detailedBook
            .seriesJson
            ?.let {
              val type = Types.newParameterizedType(List::class.java, BookSeriesDto::class.java)
              val adapter = moshi.adapter<List<BookSeriesDto>>(type)
              adapter.fromJson(it)
            }?.map {
              BookSeries(
                name = it.title,
                serialNumber = it.sequence,
              )
            } ?: emptyList(),
        progress = entity.progress?.let { mediaProgressEntityConverter.apply(it) },
      )
  }
