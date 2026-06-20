package io.github.hobin66.webdavplayer.channel.webdav

import io.github.hobin66.webdavplayer.channel.webdav.cache.WebdavBookDetailCache

fun shouldUseCachedWebdavDetail(
  cache: WebdavBookDetailCache,
  directoryEtag: String?,
  directoryLastModified: String?,
): Boolean =
  when {
    !cache.directoryEtag.isNullOrBlank() && !directoryEtag.isNullOrBlank() -> {
      cache.directoryEtag == directoryEtag
    }

    !cache.directoryLastModified.isNullOrBlank() && !directoryLastModified.isNullOrBlank() -> {
      cache.directoryLastModified == directoryLastModified
    }

    else -> {
      cache.directoryEtag.isNullOrBlank() &&
        cache.directoryLastModified.isNullOrBlank() &&
        directoryEtag.isNullOrBlank() &&
        directoryLastModified.isNullOrBlank()
    }
  }
