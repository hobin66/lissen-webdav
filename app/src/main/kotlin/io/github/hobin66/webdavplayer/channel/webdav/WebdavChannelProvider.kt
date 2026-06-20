package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.common.ChannelAuthService
import io.github.hobin66.webdavplayer.channel.common.ChannelProvider
import io.github.hobin66.webdavplayer.channel.common.MediaChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavChannelProvider
  @Inject
  constructor(
    private val webdavMediaChannel: WebdavMediaChannel,
    private val webdavAuthService: WebdavAuthService,
  ) : ChannelProvider {
    override fun provideMediaChannel(): MediaChannel = webdavMediaChannel

    override fun provideChannelAuth(): ChannelAuthService = webdavAuthService
  }
