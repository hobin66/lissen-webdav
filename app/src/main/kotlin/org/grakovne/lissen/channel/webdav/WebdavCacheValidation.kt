package org.grakovne.lissen.channel.webdav

import org.grakovne.lissen.channel.webdav.cache.WebdavBookDetailCache

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
