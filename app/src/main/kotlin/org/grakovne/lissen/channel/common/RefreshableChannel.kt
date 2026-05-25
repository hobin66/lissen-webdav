package org.grakovne.lissen.channel.common

interface RefreshableChannel {
  suspend fun refreshRemoteCache(): OperationResult<Unit>

  suspend fun refreshItemCache(itemId: String): OperationResult<Unit>
}
