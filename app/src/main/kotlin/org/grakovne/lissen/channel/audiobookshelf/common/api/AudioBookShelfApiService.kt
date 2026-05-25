package org.grakovne.lissen.channel.audiobookshelf.common.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ConnectionHost
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookShelfApiService
  @Inject
  constructor(
    private val hostProvider: AudiobookshelfHostProvider,
    private val preferences: LissenSharedPreferences,
    private val loginResponseConverter: LoginResponseConverter,
  ) {
    private var cachedHost: ConnectionHost? = null
    private var cachedToken: String? = null
    private var cachedAccessToken: String? = null
    private var cachedRefreshToken: String? = null

    private var clientCache: AudiobookshelfApiClient? = null

    private val mutex = Mutex()

    suspend fun <T> makeRequest(apiCall: suspend (client: AudiobookshelfApiClient) -> Response<T>): OperationResult<T> {
      val callResult =
        getClientInstance()
          ?.let { safeApiCall { apiCall.invoke(it) } }
          ?: return OperationResult.Error(OperationError.NetworkError)

      return when (callResult) {
        is OperationResult.Error<*> -> {
          when (callResult.code) {
            OperationError.Unauthorized -> {
              refreshToken()

              getClientInstance()
                ?.let { safeApiCall { apiCall.invoke(it) } }
                ?: return OperationResult.Error(OperationError.NetworkError)
            }

            else -> {
              callResult
            }
          }
        }

        is OperationResult.Success<*> -> {
          callResult
        }
      }
    }

    private suspend fun refreshToken() {
      mutex.withLock {
        val currentToken = preferences.getRefreshToken() ?: return@withLock

        val refreshResult =
          getClientInstance()
            ?.let { safeApiCall { it.refreshToken(currentToken) } }
            ?.map { loginResponseConverter.apply(it) }
            ?: return

        when (refreshResult) {
          is OperationResult.Error<*> -> {
            Timber.d("Refresh token update has been failed due to: $refreshResult")
            if (refreshResult.code == OperationError.Unauthorized) {
              preferences.clearCredentials()
            }
          }

          is OperationResult.Success<UserAccount> -> {
            Timber.d("Refresh token has been updated")

            refreshResult.data.refreshToken?.let {
              cachedRefreshToken = it
              preferences.saveRefreshToken(it)
            }
            refreshResult.data.accessToken?.let {
              cachedAccessToken = it
              preferences.saveAccessToken(it)
            }
          }
        }
      }
    }

    private fun getClientInstance(): AudiobookshelfApiClient? {
      val host = hostProvider.provideHost()
      val token = preferences.getToken()
      val accessToken = preferences.getAccessToken()
      val refreshToken = preferences.getRefreshToken()

      val clientChanged = isClientChanged(host, token, accessToken)
      val current = clientCache

      return when {
        current == null || clientChanged -> {
          cachedHost = host
          cachedToken = token
          cachedAccessToken = accessToken
          cachedRefreshToken = refreshToken

          createClientInstance()?.also { clientCache = it }
        }

        else -> {
          current
        }
      }
    }

    private fun createClientInstance(): AudiobookshelfApiClient? {
      val host = hostProvider.provideHost()?.url

      if (host.isNullOrBlank()) {
        return null
      }

      val client =
        ApiClient(
          host = host,
          preferences = preferences,
        )

      return client
        .retrofit
        ?.create(AudiobookshelfApiClient::class.java)
    }

    private fun isClientChanged(
      host: ConnectionHost?,
      token: String?,
      accessToken: String?,
    ) = host != cachedHost ||
      token != cachedToken ||
      accessToken != cachedAccessToken
  }
