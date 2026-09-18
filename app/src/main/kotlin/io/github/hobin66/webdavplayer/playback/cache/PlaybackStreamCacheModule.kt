package io.github.hobin66.webdavplayer.playback.cache

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide owner for the opportunistic progressive-stream cache.
 *
 * Full offline downloads use a separate persistent cache. Failure to open this cache must never
 * prevent playback, so callers treat [cacheOrNull] as an optional optimization.
 */
@Singleton
@UnstableApi
class PlaybackStreamCache
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: WebdavPlayerPreferences,
  ) {
    private val statePreferences =
      context.getSharedPreferences(STATE_PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val initializationLock = Any()

    @Volatile
    private var initializationAttempted = false

    @Volatile
    private var cache: SimpleCache? = null

    val cacheKeyFactory = CacheKeyFactory { dataSpec -> buildCacheKey(dataSpec) }

    fun cacheOrNull(): SimpleCache? {
      if (initializationAttempted) {
        return cache
      }

      return synchronized(initializationLock) {
        if (!initializationAttempted) {
          cache = openCache()
          initializationAttempted = true
          cache?.let(::clearPendingResources)
        }
        cache
      }
    }

    /**
     * Makes all existing keys unreachable first, then removes their spans independently on IO.
     * The generation change protects account switches even if an active writer delays deletion.
     */
    suspend fun invalidateAndClear() {
      advanceGeneration()
      statePreferences.edit(commit = true) { putBoolean(KEY_CLEANUP_PENDING, true) }

      withContext(Dispatchers.IO) {
        val activeCache = cacheOrNull() ?: return@withContext
        clearPendingResources(activeCache)
      }
    }

    /** Releases the cache and removes its index and files after playback has been stopped. */
    suspend fun clearForLogout() {
      advanceGeneration()
      statePreferences.edit(commit = true) { putBoolean(KEY_CLEANUP_PENDING, true) }

      withContext(Dispatchers.IO) {
        synchronized(initializationLock) {
          val activeCache = cache
          cache = null
          initializationAttempted = true

          runCatching { activeCache?.release() }
            .onFailure { error -> Timber.w(error, "Unable to release playback stream cache") }

          val cacheDir = File(context.cacheDir, STREAM_CACHE_DIR_NAME)
          val deleted =
            runCatching {
              SimpleCache.delete(cacheDir, StandaloneDatabaseProvider(context))
              if (cacheDir.exists() && !cacheDir.deleteRecursively()) {
                error("Playback stream cache directory still exists")
              }
            }.onFailure { error ->
              Timber.w(error, "Unable to delete playback stream cache on logout")
            }.isSuccess

          statePreferences.edit(commit = true) {
            putBoolean(KEY_CLEANUP_PENDING, !deleted)
          }
          initializationAttempted = false
        }
      }
    }

    private fun openCache(): SimpleCache? {
      val cacheDir = File(context.cacheDir, STREAM_CACHE_DIR_NAME)
      val evictor = LeastRecentlyUsedCacheEvictor(STREAM_CACHE_MAX_BYTES)

      return try {
        cacheDir.mkdirs()
        SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(context))
      } catch (error: Exception) {
        // A directory lock can be legitimate in another process. Never delete behind its owner.
        Timber.w(error, "Playback stream cache unavailable; continuing without stream cache")
        null
      }
    }

    private fun clearPendingResources(activeCache: SimpleCache) {
      if (!statePreferences.getBoolean(KEY_CLEANUP_PENDING, false)) {
        return
      }

      var failed = false
      activeCache.keys.toList().forEach { key ->
        runCatching { activeCache.removeResource(key) }
          .onFailure { error ->
            failed = true
            Timber.w(error, "Unable to remove one playback stream cache resource")
          }
      }

      val cleanupStillPending = failed || activeCache.keys.isNotEmpty()
      statePreferences.edit(commit = true) {
        putBoolean(KEY_CLEANUP_PENDING, cleanupStillPending)
      }
    }

    private fun buildCacheKey(dataSpec: DataSpec): String {
      val accountNamespace =
        playbackStreamAccountNamespace(
          username = runCatching { preferences.getUsername() }.getOrNull(),
          host = preferences.getHost(),
          root = preferences.getWebdavRoot(),
        )
      val generation = statePreferences.getLong(KEY_GENERATION, 0L)
      val resourceIdentity =
        dataSpec.key?.takeIf { it.isNotBlank() }
          ?: dataSpec.uri.toString()

      return buildPlaybackStreamCacheKey(
        accountNamespace = accountNamespace,
        generation = generation,
        resourceIdentity = resourceIdentity,
      )
    }

    private fun advanceGeneration() {
      synchronized(initializationLock) {
        val current = statePreferences.getLong(KEY_GENERATION, 0L)
        val next = if (current == Long.MAX_VALUE) 0L else current + 1L
        statePreferences.edit(commit = true) { putLong(KEY_GENERATION, next) }
      }
    }

    companion object {
      const val STREAM_CACHE_DIR_NAME = "playback_stream_cache"
      const val STREAM_CACHE_MAX_BYTES = 512L * 1024L * 1024L

      private const val STATE_PREFERENCES_NAME = "playback_stream_cache_state"
      private const val KEY_GENERATION = "generation"
      private const val KEY_CLEANUP_PENDING = "cleanup_pending"
    }
  }

internal fun playbackStreamAccountNamespace(
  username: String?,
  host: String?,
  root: String?,
): String =
  sha256(
    buildString {
      append(username.orEmpty())
      append('\u0000')
      append(host.orEmpty())
      append('\u0000')
      append(root.orEmpty())
    },
  )

internal fun buildPlaybackStreamCacheKey(
  accountNamespace: String,
  generation: Long,
  resourceIdentity: String,
): String = "playback-stream-v1:$accountNamespace:$generation:${sha256(resourceIdentity)}"

private fun sha256(value: String): String =
  MessageDigest
    .getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
