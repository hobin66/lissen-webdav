package org.grakovne.lissen.channel.common

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.common.withTrustedCertificates
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.util.Base64
import java.util.concurrent.TimeUnit

data class AuthOverride(
  val accessToken: String? = null,
  val token: String? = null,
  val username: String? = null,
  val webdavRoot: String? = null,
)

@Volatile
private var sharedDefaultClient: OkHttpClient? = null
private val sharedClientLock = Any()

fun createOkHttpClient(
  preferences: LissenSharedPreferences,
  authOverride: AuthOverride? = null,
): OkHttpClient {
  val default = ensureDefaultClient(preferences)
  if (authOverride == null) {
    return default
  }
  return default
    .newBuilder()
    .addInterceptor { chain -> overrideAuthInterceptor(chain, authOverride) }
    .build()
}

private fun ensureDefaultClient(preferences: LissenSharedPreferences): OkHttpClient {
  sharedDefaultClient?.let { return it }
  return synchronized(sharedClientLock) {
    sharedDefaultClient ?: buildDefaultClient(preferences).also { sharedDefaultClient = it }
  }
}

private fun buildDefaultClient(preferences: LissenSharedPreferences): OkHttpClient {
  val dispatcher =
    Dispatcher().apply {
      maxRequests = 32
      maxRequestsPerHost = 12
    }
  val connectionPool = ConnectionPool(maxIdleConnections = 12, keepAliveDuration = 5, TimeUnit.MINUTES)

  return OkHttpClient
    .Builder()
    .withTrustedCertificates()
    .addInterceptor(loggingInterceptor())
    .addInterceptor { chain -> authInterceptor(chain, preferences) }
    .dispatcher(dispatcher)
    .connectionPool(connectionPool)
    .retryOnConnectionFailure(true)
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()
}

private fun loggingInterceptor() =
  HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.NONE
  }

private fun authInterceptor(
  chain: Interceptor.Chain,
  preferences: LissenSharedPreferences,
): Response {
  val original: Request = chain.request()
  val requestBuilder: Request.Builder = original.newBuilder()
  requestBuilder.header("User-Agent", USER_AGENT)

  val authorizationHeader =
    resolveAuthorizationHeader(
      accessToken = preferences.getAccessToken(),
      token = preferences.getToken(),
      username = preferences.getUsername(),
      webdavRoot = preferences.getWebdavRoot(),
    )
  authorizationHeader?.let { requestBuilder.header("Authorization", it) }

  return chain.proceed(requestBuilder.build())
}

private fun overrideAuthInterceptor(
  chain: Interceptor.Chain,
  authOverride: AuthOverride,
): Response {
  val original = chain.request()
  val header =
    resolveAuthorizationHeader(
      accessToken = authOverride.accessToken,
      token = authOverride.token,
      username = authOverride.username,
      webdavRoot = authOverride.webdavRoot,
    )
  val rebuilt =
    when (header) {
      null -> original
      else -> original.newBuilder().header("Authorization", header).build()
    }
  return chain.proceed(rebuilt)
}

fun resolveAuthorizationHeader(
  accessToken: String?,
  token: String?,
  username: String?,
  webdavRoot: String?,
): String? =
  when {
    accessToken != null -> {
      "Bearer $accessToken"
    }

    token != null && username != null && webdavRoot != null -> {
      val encoded = Base64.getEncoder().encodeToString("$username:$token".toByteArray(Charsets.UTF_8))
      "Basic $encoded"
    }

    token != null -> {
      "Bearer $token"
    }

    else -> {
      null
    }
  }
