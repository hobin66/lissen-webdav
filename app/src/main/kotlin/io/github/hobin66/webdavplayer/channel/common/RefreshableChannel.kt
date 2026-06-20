package io.github.hobin66.webdavplayer.channel.common

interface RefreshableChannel {
  suspend fun refreshRemoteCache(): OperationResult<Unit>

  suspend fun refreshItemCache(itemId: String): OperationResult<Unit>
}
