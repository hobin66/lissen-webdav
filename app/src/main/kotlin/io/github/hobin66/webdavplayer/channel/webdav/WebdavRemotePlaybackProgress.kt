package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.webdav.model.WebdavPlaybackProgress

data class WebdavRemotePlaybackProgress(
  val bookId: String,
  val title: String,
  val author: String?,
  val progress: WebdavPlaybackProgress?,
)
