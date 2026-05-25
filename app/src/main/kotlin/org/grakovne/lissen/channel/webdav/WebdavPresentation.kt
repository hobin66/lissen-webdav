package org.grakovne.lissen.channel.webdav

fun toWebdavRootRelativePath(
  absolutePath: String,
  rootAbsolutePath: String,
): String? {
  val normalizedAbsolute = normalizeWebdavPath(absolutePath)
  val normalizedRoot = normalizeWebdavPath(rootAbsolutePath)

  if (normalizedRoot.isBlank()) {
    return normalizedAbsolute
  }

  if (normalizedAbsolute == normalizedRoot) {
    return ""
  }

  if (!normalizedAbsolute.startsWith("$normalizedRoot/")) {
    return null
  }

  return normalizedAbsolute.removePrefix("$normalizedRoot/")
}

fun buildTrackDisplayTitle(fileName: String): String {
  val extension = fileName.substringAfterLast('.', "")
  return when (extension.isBlank()) {
    true -> fileName
    false -> fileName.removeSuffix(".$extension")
  }
}

private fun normalizeWebdavPath(path: String): String =
  path
    .replace('\\', '/')
    .trim()
    .trim('/')
