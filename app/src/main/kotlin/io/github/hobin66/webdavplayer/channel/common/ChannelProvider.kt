package io.github.hobin66.webdavplayer.channel.common

interface ChannelProvider {
  fun provideMediaChannel(): MediaChannel

  fun provideChannelAuth(): ChannelAuthService
}
