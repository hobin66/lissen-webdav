package org.grakovne.lissen.channel.webdav.cache

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.lib.domain.DetailedItem

@Keep
@JsonClass(generateAdapter = true)
data class WebdavBookDetailCache(
  val bookId: String,
  val directoryEtag: String?,
  val directoryLastModified: String?,
  val item: DetailedItem,
)
