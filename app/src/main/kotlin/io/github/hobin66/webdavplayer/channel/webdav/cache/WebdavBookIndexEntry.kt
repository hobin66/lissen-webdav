package io.github.hobin66.webdavplayer.channel.webdav.cache

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import io.github.hobin66.webdavplayer.channel.webdav.model.WebdavPlaybackProgress

@Keep
@JsonClass(generateAdapter = true)
data class WebdavBookIndexEntry(
  val bookId: String,
  val directoryPath: String,
  val directoryEtag: String?,
  val directoryLastModified: String?,
  val title: String,
  val author: String? = null,
  val description: String? = null,
  val coverName: String,
  val metadataEtag: String?,
  val metadataLastModified: String?,
  val coverEtag: String?,
  val coverLastModified: String?,
  val introSkipSeconds: Int = 0,
  val outroSkipSeconds: Int = 0,
  val progress: WebdavPlaybackProgress? = null,
  val metadataPath: String? = null,
  val resolvedCoverName: String? = null,
  val isCoverMissing: Boolean = false,
  val isAdded: Boolean = false,
)

@Keep
@JsonClass(generateAdapter = true)
data class WebdavBookIndexStore(
  val items: List<WebdavBookIndexEntry>,
)
