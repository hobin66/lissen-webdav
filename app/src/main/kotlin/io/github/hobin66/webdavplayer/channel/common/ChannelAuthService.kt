package io.github.hobin66.webdavplayer.channel.common

import io.github.hobin66.webdavplayer.lib.domain.UserAccount
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences

abstract class ChannelAuthService(
  private val preferences: WebdavPlayerPreferences,
) {
  abstract suspend fun authorize(
    host: String,
    username: String,
    password: String,
    rootPath: String,
    onSuccess: suspend (UserAccount) -> Unit,
  ): OperationResult<UserAccount>

  fun persistCredentials(
    host: String,
    username: String,
    password: String,
    rootPath: String?,
  ) {
    preferences.saveHost(host)
    preferences.saveUsername(username)
    preferences.savePassword(password)
    rootPath?.let { preferences.saveWebdavRoot(it) }
  }
}
