package io.github.hobin66.webdavplayer.channel.common

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.hobin66.webdavplayer.channel.webdav.WebdavChannelProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class ChannelModule {
  @Binds
  abstract fun bindChannelProvider(provider: WebdavChannelProvider): ChannelProvider
}
