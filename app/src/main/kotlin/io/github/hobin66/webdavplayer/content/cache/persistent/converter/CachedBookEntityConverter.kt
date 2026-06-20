package io.github.hobin66.webdavplayer.content.cache.persistent.converter

import com.squareup.moshi.Types
import io.github.hobin66.webdavplayer.common.moshi
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookEntity
import io.github.hobin66.webdavplayer.content.cache.persistent.entity.BookSeriesDto
import io.github.hobin66.webdavplayer.lib.domain.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityConverter
  @Inject
  constructor() {
    fun apply(entity: BookEntity): Book =
      Book(
        id = entity.id,
        title = entity.title,
        subtitle = entity.subtitle,
        author = entity.author,
        series =
          entity
            .seriesJson
            ?.let {
              val type = Types.newParameterizedType(List::class.java, BookSeriesDto::class.java)
              val adapter = moshi.adapter<List<BookSeriesDto>>(type)
              adapter.fromJson(it)
            }?.joinToString(", ") { series ->
              buildString {
                append(series.title)
                series.sequence
                  ?.takeIf(String::isNotBlank)
                  ?.let { append(" #$it") }
              }
            },
      )
  }
