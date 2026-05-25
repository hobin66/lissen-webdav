package org.grakovne.lissen.channel.audiobookshelf

import org.grakovne.lissen.channel.common.ConnectionHost
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfHostProvider
  @Inject
  constructor(
    private val sharedPreferences: LissenSharedPreferences,
  ) {
    fun provideHost(): ConnectionHost? =
      sharedPreferences
        .getHost()
        ?.let(ConnectionHost.Companion::external)
        ?.also { Timber.d("Using external host: ${it.url}") }
  }
