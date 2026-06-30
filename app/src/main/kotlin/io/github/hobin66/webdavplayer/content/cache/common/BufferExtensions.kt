package io.github.hobin66.webdavplayer.content.cache.common

import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File

fun Buffer.writeToFile(file: File) {
  file.parentFile?.mkdirs()
  val temporaryFile = file.temporarySibling()

  try {
    temporaryFile.delete()
    temporaryFile.sink().buffer().use { sink ->
      sink.write(this, size)
      sink.flush()
    }
    temporaryFile.replaceAtomically(file)
  } catch (ex: Exception) {
    temporaryFile.delete()
    throw ex
  }
}

internal fun File.temporarySibling(): File =
  parentFile
    ?.resolve("$name.tmp")
    ?: File("$path.tmp")

internal fun File.replaceAtomically(target: File) {
  if (target.exists() && target.delete().not()) {
    error("Unable to replace existing file ${target.absolutePath}")
  }
  if (renameTo(target).not()) {
    error("Unable to rename $absolutePath to ${target.absolutePath}")
  }
}
