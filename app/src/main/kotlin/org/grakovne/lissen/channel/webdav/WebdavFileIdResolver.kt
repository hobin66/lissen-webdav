package org.grakovne.lissen.channel.webdav

fun resolveWebdavFileRelativePath(fileId: String): String? {
  WebdavPathCodec.decode(fileId)?.let { decoded ->
    if (decoded.isNotBlank()) {
      return decoded
    }
  }

  val fallback = fileId.trim().replace('\\', '/')
  if (fallback.isBlank()) {
    return null
  }

  return when {
    fallback.contains("/") -> fallback
    fallback.isAudioFileName() -> fallback
    else -> null
  }
}

private fun String.isAudioFileName(): Boolean =
  lowercase().endsWith(".mp3") ||
    lowercase().endsWith(".m4a") ||
    lowercase().endsWith(".m4b") ||
    lowercase().endsWith(".aac") ||
    lowercase().endsWith(".flac") ||
    lowercase().endsWith(".ogg") ||
    lowercase().endsWith(".opus") ||
    lowercase().endsWith(".wav") ||
    lowercase().endsWith(".mp4")
