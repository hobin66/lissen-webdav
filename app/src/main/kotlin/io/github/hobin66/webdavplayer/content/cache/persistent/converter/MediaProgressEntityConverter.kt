package io.github.hobin66.webdavplayer.content.cache.persistent.converter

import io.github.hobin66.webdavplayer.content.cache.persistent.entity.MediaProgressEntity
import io.github.hobin66.webdavplayer.lib.domain.MediaProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProgressEntityConverter
  @Inject
  constructor() {
    fun apply(entity: MediaProgressEntity): MediaProgress =
      MediaProgress(
        currentTime = entity.currentTime,
        isFinished = entity.isFinished,
        lastUpdate = entity.lastUpdate,
      )
  }
