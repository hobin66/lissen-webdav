package org.grakovne.lissen.channel.webdav.model

data class WebdavResource(
  val relativePath: String,
  val name: String,
  val isDirectory: Boolean,
  val eTag: String?,
  val lastModified: String?,
  val size: Long?,
  val mimeType: String?,
)
