package io.github.hobin66.webdavplayer.content.cache.persistent

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.hobin66.webdavplayer.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface ContentAutoCachingModule {
  @Binds
  @IntoSet
  fun bindContentAutoCachingService(service: ContentAutoCachingService): RunningComponent
}
