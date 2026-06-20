package io.github.hobin66.webdavplayer.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.hobin66.webdavplayer.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface PlaybackEnhancerModule {
  @Binds
  @IntoSet
  fun bindPlaybackEnhancerService(service: PlaybackEnhancerService): RunningComponent
}
