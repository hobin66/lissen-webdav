package io.github.hobin66.webdavplayer.channel.webdav

import android.net.Uri
import io.github.hobin66.webdavplayer.channel.common.ChannelProvider
import io.github.hobin66.webdavplayer.channel.common.USER_AGENT
import io.github.hobin66.webdavplayer.channel.common.resolveAuthorizationHeader
import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavPersistentCache
import io.github.hobin66.webdavplayer.content.cache.persistent.LocalCacheRepository
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily resolves missing chapter/file durations.
 *
 * Callers should not block book open / prepare on a full-book probe. Prefer
 * [preferredFileIds] (current chapter neighborhood) first, then remaining tracks.
 */
@Singleton
class ChapterDurationEnricher
  @Inject
  constructor(
    private val preferences: WebdavPlayerPreferences,
    private val localCacheRepository: LocalCacheRepository,
    private val channelProvider: ChannelProvider,
    private val persistentCache: WebdavPersistentCache,
    private val audioDurationProbe: AudioDurationProbe,
  ) {
    /**
     * Applies durations discovered during playback (ExoPlayer) and persists them when possible.
     */
    suspend fun applyKnownDurations(
      item: DetailedItem,
      durationsByFileId: Map<String, Double>,
    ): DetailedItem {
      val sanitized = durationsByFileId.filterValues(::hasResolvedDuration)
      if (sanitized.isEmpty()) {
        return item
      }

      val enriched = applyResolvedFileDurations(item, sanitized)
      persistEnrichedDetail(enriched)
      return enriched
    }

    /**
     * Probes unresolved durations.
     *
     * @param preferredFileIds probed first (e.g. current chapter and neighbors)
     * @param maxFiles maximum number of files to attempt (preferred count toward the limit)
     * @param onPartial invoked after each small batch that resolves at least one duration
     */
    suspend fun enrich(
      item: DetailedItem,
      preferredFileIds: Collection<String> = emptyList(),
      maxFiles: Int = Int.MAX_VALUE,
      onPartial: (suspend (DetailedItem) -> Unit)? = null,
    ): DetailedItem {
      if (!needsChapterDurationResolution(item) || maxFiles <= 0) {
        return item
      }

      val unresolvedIds =
        item.files
          .asSequence()
          .filter { !hasResolvedDuration(it.duration) }
          .map { it.id }
          .toList()

      if (unresolvedIds.isEmpty()) {
        return item
      }

      val preferredSet = preferredFileIds.toSet()
      val orderedIds =
        buildList {
          preferredFileIds.forEach { id ->
            if (id in unresolvedIds && id !in this) {
              add(id)
            }
          }
          unresolvedIds.forEach { id ->
            if (id !in preferredSet && id !in this) {
              add(id)
            }
          }
        }.take(maxFiles)

      if (orderedIds.isEmpty()) {
        return item
      }

      var current = item
      orderedIds.chunked(PROBE_BATCH_SIZE).forEachIndexed { index, batch ->
        current = probeAndApply(current, batch, onPartial)
        currentCoroutineContext().ensureActive()
        if (index < orderedIds.lastIndex / PROBE_BATCH_SIZE) {
          delay(PROBE_BATCH_PAUSE_MS)
        }
      }

      return current
    }

    private suspend fun probeAndApply(
      item: DetailedItem,
      fileIds: List<String>,
      onPartial: (suspend (DetailedItem) -> Unit)?,
    ): DetailedItem {
      if (fileIds.isEmpty()) {
        return item
      }

      val headers = buildProbeHeaders()

      val probed =
        coroutineScope {
          fileIds
            .map { fileId ->
              async {
                val duration =
                  withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    probeFileDuration(
                      bookId = item.id,
                      fileId = fileId,
                      headers = headers,
                    )
                  }

                duration
                  ?.takeIf(::hasResolvedDuration)
                  ?.let { fileId to it }
              }
            }.awaitAll()
            .filterNotNull()
            .toMap()
        }

      if (probed.isEmpty()) {
        Timber.d(
          "No chapter durations resolved for bookId=%s among %d candidates",
          item.id,
          fileIds.size,
        )
        return item
      }

      val enriched = applyResolvedFileDurations(item, probed)
      persistEnrichedDetail(enriched)

      Timber.d(
        "Resolved %d/%d chapter durations for bookId=%s",
        probed.size,
        fileIds.size,
        item.id,
      )

      onPartial?.invoke(enriched)
      return enriched
    }

    private suspend fun probeFileDuration(
      bookId: String,
      fileId: String,
      headers: Map<String, String>,
    ): Double? {
      val localUri = localCacheRepository.provideFileUri(bookId, fileId)
      if (localUri != null) {
        audioDurationProbe.probeDurationSeconds(localUri)?.let { return it }
      }

      val remoteUri = resolveRemoteUri(bookId, fileId) ?: return null
      return audioDurationProbe.probeDurationSeconds(remoteUri, headers)
    }

    private fun resolveRemoteUri(
      bookId: String,
      fileId: String,
    ): Uri? {
      val uri = channelProvider.provideMediaChannel().provideFileUri(bookId, fileId)
      if (uri == Uri.EMPTY || uri.scheme.isNullOrBlank()) {
        return null
      }
      return uri
    }

    private fun buildProbeHeaders(): Map<String, String> {
      val headers = linkedMapOf("User-Agent" to USER_AGENT)
      resolveAuthorizationHeader(
        username = preferences.getUsername(),
        password = preferences.getPassword(),
        webdavRoot = preferences.getWebdavRoot(),
      )?.let { headers["Authorization"] = it }
      return headers
    }

    private suspend fun persistEnrichedDetail(item: DetailedItem) {
      val incomingDurations =
        item.files
          .filter { hasResolvedDuration(it.duration) }
          .associate { it.id to it.duration }

      try {
        persistentCache.updateBookDetail(item.id) { existing ->
          existing.copy(
            item = applyResolvedFileDurations(existing.item, incomingDurations),
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Timber.w(error, "Unable to persist enriched durations for bookId=%s", item.id)
      }
    }

    companion object {
      private const val PROBE_BATCH_SIZE = 3
      private const val PROBE_BATCH_PAUSE_MS = 250L
      private const val PROBE_TIMEOUT_MS = 8_000L

      /** Files to probe immediately around the current chapter before background remainder. */
      const val PRIORITY_PROBE_MAX_FILES = 3
    }
  }
