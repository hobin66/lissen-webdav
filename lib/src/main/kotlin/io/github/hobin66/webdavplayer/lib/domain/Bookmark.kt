package io.github.hobin66.webdavplayer.lib.domain

data class Bookmark(
	val id: String,
	val libraryItemId: String,
	val title: String,
	val totalPosition: Double,
	val createdAt: Long,
	val syncState: BookmarkSyncState,
	val chapterId: String? = null,
	val chapterPosition: Double? = null,
)

fun Bookmark.isSame(other: Bookmark): Boolean =
	libraryItemId == other.libraryItemId &&
		when {
			chapterId != null && other.chapterId != null -> {
				chapterId == other.chapterId && chapterPosition == other.chapterPosition
			}
			else -> {
				totalPosition == other.totalPosition
			}
		}
