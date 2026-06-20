package io.github.hobin66.webdavplayer.content.cache.persistent

import androidx.annotation.Keep
import io.github.hobin66.webdavplayer.lib.domain.CacheStatus

@Keep
data class CacheState(
  val status: CacheStatus,
  val progress: Double = 0.0,
)
