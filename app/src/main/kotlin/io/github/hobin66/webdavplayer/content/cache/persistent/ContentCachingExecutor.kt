package io.github.hobin66.webdavplayer.content.cache.persistent

import kotlinx.coroutines.flow.Flow
import io.github.hobin66.webdavplayer.channel.common.MediaChannel
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.DownloadOption

class ContentCachingExecutor(
  private val item: DetailedItem,
  private val options: DownloadOption,
  private val position: Double,
  private val contentCachingManager: ContentCachingManager,
) {
  fun run(channel: MediaChannel): Flow<CacheState> =
    contentCachingManager
      .cacheMediaItem(
        mediaItem = item,
        option = options,
        channel = channel,
        currentTotalPosition = position,
      )
}
