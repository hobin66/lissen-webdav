package org.grakovne.lissen.channel.webdav

import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.ChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
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
