package io.github.hobin66.webdavplayer.playback.service

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.hobin66.webdavplayer.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface PlaybackNavigationModule {
  @Binds
  @IntoSet
  fun bindPlaybackNavigationService(service: PlaybackNavigationService): RunningComponent
}
