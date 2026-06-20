package io.github.hobin66.webdavplayer.channel.webdav.cache

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem

@Keep
@JsonClass(generateAdapter = true)
data class WebdavBookDetailCache(
  val bookId: String,
  val directoryEtag: String?,
  val directoryLastModified: String?,
  val item: DetailedItem,
)
