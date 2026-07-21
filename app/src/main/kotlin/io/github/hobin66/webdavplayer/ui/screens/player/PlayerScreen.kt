package io.github.hobin66.webdavplayer.ui.screens.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.ImageLoader
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.content.PlaybackProgressSyncDirection
import io.github.hobin66.webdavplayer.lib.domain.DetailedItem
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.ui.icons.Search
import io.github.hobin66.webdavplayer.ui.navigation.AppNavigationService
import io.github.hobin66.webdavplayer.ui.screens.player.composable.BookmarksComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.MediaDetailComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.NavigationBarComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.PlayingQueueComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.TrackControlComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.TrackDetailsComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.common.provideNowPlayingTitle
import io.github.hobin66.webdavplayer.ui.screens.player.composable.fallback.PlayingQueueFallbackComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.placeholder.NavigationBarPlaceholderComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import io.github.hobin66.webdavplayer.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import io.github.hobin66.webdavplayer.viewmodel.CachingModelView
import io.github.hobin66.webdavplayer.viewmodel.PlayerViewModel
import io.github.hobin66.webdavplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
  navController: AppNavigationService,
  imageLoader: ImageLoader,
  bookId: String,
  bookTitle: String,
  bookSubtitle: String?,
  playInstantly: Boolean,
) {
  val context = LocalContext.current

  val cachingModelView: CachingModelView = hiltViewModel()
  val playerViewModel: PlayerViewModel = hiltViewModel()
  val settingsViewModel: SettingsViewModel = hiltViewModel()

  val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

  val playingBook by playerViewModel.book.observeAsState()
  val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)
  val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)
  val searchRequested by playerViewModel.searchRequested.observeAsState(false)
  val bookRefreshInProgress by playerViewModel.bookRefreshInProgress.observeAsState(false)
  val bookRefreshMessageRes by playerViewModel.bookRefreshMessageRes.observeAsState()
  val bookSkipSaveMessageRes by playerViewModel.bookSkipSaveMessageRes.observeAsState()

  var itemDetailsSelected by remember { mutableStateOf(false) }
  var bookmarksSelected by remember { mutableStateOf(false) }
  var currentBookActionSheetVisible by remember { mutableStateOf(false) }
  var refreshConfirmationVisible by remember { mutableStateOf(false) }
  var pendingSyncDirection by remember { mutableStateOf<PlaybackProgressSyncDirection?>(null) }

  val screenTitle =
    when (playingQueueExpanded) {
      true -> provideNowPlayingTitle(LibraryType.LIBRARY, context)
      false -> stringResource(R.string.player_screen_title)
    }

  fun stepBack() {
    when (
      resolvePlayerBackAction(
        searchRequested = searchRequested,
        playingQueueExpanded = playingQueueExpanded,
        canGoBack = navController.canGoBack(),
      )
    ) {
      PlayerBackAction.DISMISS_SEARCH -> playerViewModel.dismissSearch()
      PlayerBackAction.COLLAPSE_QUEUE -> playerViewModel.collapsePlayingQueue()
      PlayerBackAction.GO_BACK -> navController.goBack()
      PlayerBackAction.SHOW_LIBRARY -> navController.showLibrary(clearHistory = true)
    }
  }

  BackHandler(enabled = searchRequested || playingQueueExpanded || playInstantly) {
    stepBack()
  }

  LaunchedEffect(Unit) {
    bookId
      .takeIf { playingItemChanged(it, playingBook) || cachePolicyChanged(cachingModelView, playingBook) }
      ?.let {
        if (settingsViewModel.hasCredentials().not()) {
          navController.showLogin()
          return@LaunchedEffect
        }

        playerViewModel.preparePlayback(it)
      }

    if (playInstantly) {
      playerViewModel.prepareAndPlay()
    }
  }

  LaunchedEffect(playingQueueExpanded) {
    if (playingQueueExpanded.not()) {
      playerViewModel.dismissSearch()
    }
  }

  LaunchedEffect(playingBook) {
    playerViewModel.updateBookmarks()
  }

  LaunchedEffect(bookRefreshMessageRes) {
    val messageRes = bookRefreshMessageRes ?: return@LaunchedEffect
    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    playerViewModel.consumeBookRefreshMessage()
  }

  LaunchedEffect(bookSkipSaveMessageRes) {
    val messageRes = bookSkipSaveMessageRes ?: return@LaunchedEffect
    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    playerViewModel.consumeBookSkipSaveMessage()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        actions = {
          if (playingQueueExpanded) {
            AnimatedContent(
              targetState = searchRequested,
              label = "library_action_animation",
              transitionSpec = {
                fadeIn(animationSpec = keyframes { durationMillis = 150 }) togetherWith
                  fadeOut(animationSpec = keyframes { durationMillis = 150 })
              },
            ) { isSearchRequested ->
              when (isSearchRequested) {
                true -> {
                  ChapterSearchActionComposable(
                    onSearchRequested = { playerViewModel.updateSearch(it) },
                  )
                }

                false -> {
                  Row {
                    IconButton(
                      onClick = { playerViewModel.requestSearch() },
                      modifier = Modifier.padding(end = 4.dp),
                    ) {
                      Icon(
                        imageVector = Search,
                        contentDescription = null,
                      )
                    }
                  }
                }
              }
            }
          } else {
            Row {
              if (settingsViewModel.canSyncPlaybackProgress()) {
                IconButton(
                  onClick = { currentBookActionSheetVisible = true },
                  enabled = !bookRefreshInProgress,
                  modifier = Modifier.padding(end = 4.dp),
                ) {
                  when (bookRefreshInProgress) {
                    true -> {
                      CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                      )
                    }

                    false -> {
                      Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.player_current_book_action_title),
                      )
                    }
                  }
                }
              }

              IconButton(
                onClick = {
                  if (isPlaybackReady) {
                    playerViewModel.updateBookmarks()
                    bookmarksSelected = true
                  }
                },
                modifier = Modifier.padding(end = 4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Bookmarks,
                  contentDescription = null,
                )
              }

              IconButton(
                onClick = { itemDetailsSelected = true },
                modifier = Modifier.padding(end = 4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Info,
                  contentDescription = null,
                )
              }
            }
          }
        },
        title = {
          Text(
            text = screenTitle,
            style = titleTextStyle,
            color = colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        navigationIcon = {
          IconButton(onClick = { stepBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = null,
              tint = colorScheme.onSurface,
            )
          }
        },
      )
    },
    bottomBar = {
      if (playingBook == null || isPlaybackReady.not()) {
        NavigationBarPlaceholderComposable(libraryType = LibraryType.LIBRARY)
      } else {
        playingBook
          ?.let {
            NavigationBarComposable(
              book = it,
              playerViewModel = playerViewModel,
              contentCachingModelView = cachingModelView,
              navController = navController,
              libraryType = LibraryType.LIBRARY,
            )
          }
      }
    },
    modifier = Modifier.systemBarsPadding(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .testTag("playerScreen")
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        AnimatedVisibility(
          visible = playingQueueExpanded.not(),
          enter = expandVertically(animationSpec = tween(400)),
          exit = shrinkVertically(animationSpec = tween(400)),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            if (!isPlaybackReady) {
              TrackDetailsPlaceholderComposable(bookTitle, bookSubtitle)
            } else {
              TrackDetailsComposable(
                viewModel = playerViewModel,
                imageLoader = imageLoader,
              )
            }

            if (!isPlaybackReady) {
              TrackControlPlaceholderComposable(
                modifier = Modifier,
                settingsViewModel = settingsViewModel,
              )
            } else {
              TrackControlComposable(
                viewModel = playerViewModel,
                modifier = Modifier,
                settingsViewModel = settingsViewModel,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        when {
          isPlaybackReady.not() -> {
            PlayingQueuePlaceholderComposable(
              modifier = Modifier,
            )
          }

          playingBook?.chapters.isNullOrEmpty() -> {
            PlayingQueueFallbackComposable(
              modifier = Modifier,
            )
          }

          else -> {
            PlayingQueueComposable(
              cachingModelView = cachingModelView,
              viewModel = playerViewModel,
              modifier = Modifier,
            )
          }
        }
      }
    },
  )

  if (itemDetailsSelected) {
    MediaDetailComposable(
      playingBook = playingBook,
      playingViewModel = playerViewModel,
      settingsViewModel = settingsViewModel,
      onDismissRequest = { itemDetailsSelected = false },
    )
  }

  if (bookmarksSelected) {
    BookmarksComposable(
      playerViewModel = playerViewModel,
      onDismissRequest = { bookmarksSelected = false },
    )
  }

  if (currentBookActionSheetVisible) {
    CurrentBookActionSheet(
      onLocalToRemoteSelected = {
        currentBookActionSheetVisible = false
        pendingSyncDirection = PlaybackProgressSyncDirection.LOCAL_TO_REMOTE
      },
      onRemoteToLocalSelected = {
        currentBookActionSheetVisible = false
        pendingSyncDirection = PlaybackProgressSyncDirection.REMOTE_TO_LOCAL
      },
      onRefreshCacheSelected = {
        currentBookActionSheetVisible = false
        refreshConfirmationVisible = true
      },
      onDismissRequest = { currentBookActionSheetVisible = false },
    )
  }

  if (refreshConfirmationVisible) {
    AlertDialog(
      onDismissRequest = { refreshConfirmationVisible = false },
      title = { Text(text = stringResource(R.string.player_refresh_current_book_cache_title)) },
      text = { Text(text = stringResource(R.string.player_refresh_current_book_cache_confirm_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            refreshConfirmationVisible = false
            playerViewModel.refreshCurrentBook(playingBook?.id ?: bookId)
          },
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { refreshConfirmationVisible = false }) {
          Text(text = stringResource(android.R.string.cancel))
        }
      },
    )
  }

  pendingSyncDirection?.let { direction ->
    AlertDialog(
      onDismissRequest = { pendingSyncDirection = null },
      title = {
        Text(
          text =
            when (direction) {
              PlaybackProgressSyncDirection.LOCAL_TO_REMOTE -> {
                stringResource(R.string.settings_sync_playback_progress_local_to_remote)
              }

              PlaybackProgressSyncDirection.REMOTE_TO_LOCAL -> {
                stringResource(R.string.settings_sync_playback_progress_remote_to_local)
              }
            },
        )
      },
      text = {
        Text(
          text =
            when (direction) {
              PlaybackProgressSyncDirection.LOCAL_TO_REMOTE -> {
                stringResource(R.string.player_sync_current_book_local_to_remote_confirm_message)
              }

              PlaybackProgressSyncDirection.REMOTE_TO_LOCAL -> {
                stringResource(R.string.player_sync_current_book_remote_to_local_confirm_message)
              }
            },
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingSyncDirection = null
            playerViewModel.syncCurrentBookProgress(playingBook?.id ?: bookId, direction)
          },
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingSyncDirection = null }) {
          Text(text = stringResource(android.R.string.cancel))
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrentBookActionSheet(
  onLocalToRemoteSelected: () -> Unit,
  onRemoteToLocalSelected: () -> Unit,
  onRefreshCacheSelected: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(bottom = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Sync,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.player_current_book_action_title),
            style = typography.bodyLarge,
          )
        }

        CurrentBookActionButton(
          icon = Icons.Outlined.CloudUpload,
          title = stringResource(R.string.player_sync_current_book_local_to_remote_title),
          description = stringResource(R.string.player_sync_current_book_local_to_remote_description),
          onClick = onLocalToRemoteSelected,
        )

        CurrentBookActionButton(
          icon = Icons.Outlined.CloudDownload,
          title = stringResource(R.string.player_sync_current_book_remote_to_local_title),
          description = stringResource(R.string.player_sync_current_book_remote_to_local_description),
          onClick = onRemoteToLocalSelected,
        )

        CurrentBookActionButton(
          icon = Icons.Outlined.Refresh,
          title = stringResource(R.string.player_refresh_current_book_cache_title),
          description = stringResource(R.string.player_refresh_current_book_cache_description),
          onClick = onRefreshCacheSelected,
        )
      }
    },
  )
}

@Composable
private fun CurrentBookActionButton(
  icon: ImageVector,
  title: String,
  description: String,
  onClick: () -> Unit,
) {
  FilledTonalButton(
    onClick = onClick,
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
    colors =
      ButtonDefaults.filledTonalButtonColors(
        containerColor = colorScheme.surfaceContainer,
        contentColor = colorScheme.onSurface,
      ),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(horizontalAlignment = Alignment.Start) {
        Text(
          text = title,
          style = typography.bodyMedium.copy(color = colorScheme.primary),
        )
        Text(
          text = description,
          style = typography.bodySmall.copy(color = colorScheme.onSurfaceVariant),
        )
      }
    }
  }
}

@Composable
fun InfoRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  textValue: String,
) {
  Spacer(modifier = Modifier.height(8.dp))

  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = "$label: ",
      style = typography.bodyMedium,
      color = Color.Gray,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = textValue,
      style = typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun playingItemChanged(
  item: String,
  playingBook: DetailedItem?,
) = item != playingBook?.id

private fun cachePolicyChanged(
  cachingModelView: CachingModelView,
  playingBook: DetailedItem?,
) = cachingModelView.localCacheUsing() != playingBook?.localProvided
