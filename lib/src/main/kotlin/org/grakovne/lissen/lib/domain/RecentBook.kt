package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class RecentBook(
  val id: String,
  val title: String,
  val subtitle: String?,
  val author: String?,
  val listenedPercentage: Int?,
  val listenedLastUpdate: Long?,
)
