package org.grakovne.lissen.channel.webdav.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class WebdavBookMetadata(
  val version: Int = 1,
  val id: String,
  val title: String,
  val author: String? = null,
  val description: String? = null,
  val cover: String?,
  val introSkipSeconds: Int = 0,
  val outroSkipSeconds: Int = 0,
) {
  fun coverOrDefault(): String = cover ?: "cover.jpg"

  fun authorOrNull(): String? = author?.trim()?.takeIf { it.isNotEmpty() }

  fun descriptionOrNull(): String? = description?.trim()?.takeIf { it.isNotEmpty() }

  fun introSkipSecondsOrDefault(): Int = introSkipSeconds.coerceIn(0, 60)

  fun outroSkipSecondsOrDefault(): Int = outroSkipSeconds.coerceIn(0, 60)
}
