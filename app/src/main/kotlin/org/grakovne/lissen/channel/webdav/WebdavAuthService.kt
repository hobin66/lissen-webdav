package org.grakovne.lissen.channel.webdav

import org.grakovne.lissen.channel.common.AuthData
import org.grakovne.lissen.channel.common.AuthMethod
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.webdav.client.WebdavClient
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavAuthService
  @Inject
  constructor(
    preferences: LissenSharedPreferences,
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
              token = password,
              accessToken = null,
              refreshToken = null,
              username = username,
              preferredLibraryId = WebdavMediaChannel.WEBDAV_LIBRARY_ID,
            )

          onSuccess(account)
          OperationResult.Success(account)
        },
        onFailure = { OperationResult.Error(it.code) },
      )
    }

    override suspend fun startOAuth(
      host: String,
      onSuccess: () -> Unit,
      onFailure: (OperationError) -> Unit,
    ) {
      onFailure(OperationError.UnsupportedError)
    }

    override suspend fun exchangeToken(
      host: String,
      code: String,
      onSuccess: suspend (UserAccount) -> Unit,
      onFailure: (String) -> Unit,
    ) {
      onFailure("webdav_oauth_not_supported")
    }

    override suspend fun fetchAuthMethods(host: String): OperationResult<AuthData> =
      OperationResult.Success(
        AuthData(
          methods = listOf(AuthMethod.CREDENTIALS),
          oauthLoginText = null,
        ),
      )

    private companion object {
      val urlPattern = Regex("^(http|https)://.*\$")
    }
  }
