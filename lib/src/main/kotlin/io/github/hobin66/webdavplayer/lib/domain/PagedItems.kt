package io.github.hobin66.webdavplayer.lib.domain

import androidx.annotation.Keep

@Keep
data class PagedItems<T>(
	val items: List<T>,
	val currentPage: Int,
	val totalItems: Int
)
