package io.github.hobin66.webdavplayer.lib.domain

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class ContentCachingTask(
  val item: DetailedItem,
  val options: DownloadOption,
  val currentPosition: Double,
) : Serializable
