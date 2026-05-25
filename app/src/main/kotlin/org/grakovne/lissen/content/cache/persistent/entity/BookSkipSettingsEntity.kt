package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "book_skip_settings")
data class BookSkipSettingsEntity(
  @PrimaryKey val bookId: String,
  val introSkipSeconds: Int,
  val outroSkipSeconds: Int,
)
