package org.grakovne.lissen.channel.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.channel.common.USER_AGENT
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

fun createOkHttpClient(
  preferences: LissenSharedPreferences,
  authOverride: AuthOverride? = null,
): OkHttpClient =
  OkHttpClient
    .Builder()
    .withTrustedCertificates()
    .addInterceptor(loggingInterceptor())
    .addInterceptor { chain -> authInterceptor(chain, preferences, authOverride) }
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private fun loggingInterceptor() =
  HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.NONE
  }

private fun authInterceptor(
  chain: Interceptor.Chain,
  preferences: LissenSharedPreferences,
  authOverride: AuthOverride?,
): Response {
  val original: Request = chain.request()
  val requestBuilder: Request.Builder = original.newBuilder()
  requestBuilder.header("User-Agent", USER_AGENT)

  val authorizationHeader =
    resolveAuthorizationHeader(
      accessToken = authOverride?.accessToken ?: preferences.getAccessToken(),
      token = authOverride?.token ?: preferences.getToken(),
      username = authOverride?.username ?: preferences.getUsername(),
      webdavRoot = authOverride?.webdavRoot ?: preferences.getWebdavRoot(),
    )
  authorizationHeader?.let { requestBuilder.header("Authorization", it) }

  return chain.proceed(requestBuilder.build())
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
