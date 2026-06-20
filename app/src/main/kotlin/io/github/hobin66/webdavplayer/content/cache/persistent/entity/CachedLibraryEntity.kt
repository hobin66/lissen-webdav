package io.github.hobin66.webdavplayer.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import java.io.Serializable

@Keep
@Entity(
  tableName = "libraries",
)
@JsonClass(generateAdapter = true)
data class CachedLibraryEntity(
  @PrimaryKey
  val id: String,
  val title: String,
  val type: LibraryType,
) : Serializable
