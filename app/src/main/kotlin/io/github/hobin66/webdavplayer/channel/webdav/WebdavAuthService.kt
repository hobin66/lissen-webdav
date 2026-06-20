package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.common.ChannelAuthService
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.channel.webdav.client.WebdavClient
import io.github.hobin66.webdavplayer.lib.domain.UserAccount
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavAuthService
  @Inject
  constructor(
    preferences: WebdavPlayerPreferences,
    private val webdavClient: WebdavClient,
  ) : ChannelAuthService(preferences) {
    override suspend fun authorize(
      host: String,
      username: String,
      password: String,
      rootPath: String,
      onSuccess: suspend (UserAccount) -> Unit,
    ): OperationResult<UserAccount> {
      if (host.isBlank() || !urlPattern.matches(host)) {
        return OperationResult.Error(OperationError.InvalidCredentialsHost)
      }

      if (rootPath.isBlank()) {
        return OperationResult.Error(OperationError.MissingCredentialsRootPath)
      }

      if (username.isBlank()) {
        return OperationResult.Error(OperationError.MissingCredentialsUsername)
      }

      if (password.isBlank()) {
        return OperationResult.Error(OperationError.MissingCredentialsPassword)
      }

      val probe =
        webdavClient.checkRootAvailability(
          host = host,
          rootPath = rootPath,
          username = username,
          password = password,
        )

      return probe.foldAsync(
        onSuccess = {
          val account =
            UserAccount(
              username = username,
              password = password,
            )

          onSuccess(account)
          OperationResult.Success(account)
        },
        onFailure = { OperationResult.Error(it.code) },
      )
    }

    private companion object {
      val urlPattern = Regex("^(http|https)://.*\$")
    }
  }
