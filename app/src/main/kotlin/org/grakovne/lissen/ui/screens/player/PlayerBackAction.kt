package org.grakovne.lissen.ui.screens.player

internal enum class PlayerBackAction {
  DISMISS_SEARCH,
  COLLAPSE_QUEUE,
  GO_BACK,
  SHOW_LIBRARY,
}

internal fun resolvePlayerBackAction(
  searchRequested: Boolean,
  playingQueueExpanded: Boolean,
  canGoBack: Boolean,
): PlayerBackAction =
  when {
    searchRequested -> PlayerBackAction.DISMISS_SEARCH
    playingQueueExpanded -> PlayerBackAction.COLLAPSE_QUEUE
    canGoBack -> PlayerBackAction.GO_BACK
    else -> PlayerBackAction.SHOW_LIBRARY
  }
