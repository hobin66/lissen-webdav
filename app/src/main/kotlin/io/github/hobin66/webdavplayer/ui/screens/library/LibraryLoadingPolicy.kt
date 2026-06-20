package io.github.hobin66.webdavplayer.ui.screens.library

fun shouldShowLibraryPlaceholder(
  searchRequested: Boolean,
  pullRefreshing: Boolean,
  recentBookRefreshing: Boolean,
  libraryRefreshing: Boolean,
  hasDisplayedContent: Boolean,
  forceRemoteRefreshPlaceholder: Boolean = false,
): Boolean {
  if (searchRequested) {
    return false
  }

  if (forceRemoteRefreshPlaceholder) {
    return true
  }

  if (hasDisplayedContent) {
    return false
  }

  return pullRefreshing || recentBookRefreshing || libraryRefreshing
}

fun shouldForceLibraryReloadAfterRemoteRefresh(
  remoteRefreshPending: Boolean,
  pullRefreshing: Boolean,
): Boolean = remoteRefreshPending && !pullRefreshing
