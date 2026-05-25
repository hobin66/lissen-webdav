package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.webdav.WebdavManageBookItem
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.viewmodel.LibraryManageViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
fun LibraryManageScreen(
  onBack: () -> Unit,
  imageLoader: ImageLoader,
  viewModel: LibraryManageViewModel = hiltViewModel(),
) {
  val books by viewModel.books.observeAsState(emptyList())
  val loading by viewModel.loading.observeAsState(false)
  val updatingIds by viewModel.updatingBookIds.observeAsState(emptySet())
  val messageRes by viewModel.messageRes.observeAsState()

  val context = LocalContext.current

  LaunchedEffect(Unit) {
    viewModel.loadBooks(forceRefresh = true)
  }

  LaunchedEffect(messageRes) {
    val res = messageRes ?: return@LaunchedEffect
    Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
    viewModel.consumeMessage()
  }

  val pullRefreshState =
    rememberPullRefreshState(
      refreshing = loading,
      onRefresh = { viewModel.loadBooks(forceRefresh = true) },
    )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.library_manage_books_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = colorScheme.onSurface,
            )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxSize(),
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .padding(innerPadding)
          .pullRefresh(pullRefreshState)
          .fillMaxSize(),
    ) {
      when (books.isEmpty() && !loading) {
        true -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.library_manage_books_empty),
              style = typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }

        false -> {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
              items = books,
              key = { it.id },
            ) { item ->
              LibraryManageItemComposable(
                item = item,
                imageLoader = imageLoader,
                enabled = updatingIds.contains(item.id).not(),
                onAction = { viewModel.toggleBook(item) },
              )
              HorizontalDivider()
            }
          }
        }
      }

      PullRefreshIndicator(
        refreshing = loading,
        state = pullRefreshState,
        contentColor = colorScheme.primary,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

@Composable
private fun LibraryManageItemComposable(
  item: WebdavManageBookItem,
  imageLoader: ImageLoader,
  enabled: Boolean,
  onAction: () -> Unit,
) {
  val context = LocalContext.current
  val imageRequest =
    ImageRequest
      .Builder(context)
      .data(item.id)
      .build()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncShimmeringImage(
      imageRequest = imageRequest,
      imageLoader = imageLoader,
      contentDescription = "${item.title} cover",
      contentScale = ContentScale.FillBounds,
      modifier =
        Modifier
          .size(64.dp)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(4.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title,
        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(Modifier.width(12.dp))

    when (item.isAdded) {
      true -> {
        OutlinedButton(
          onClick = onAction,
          enabled = enabled,
        ) {
          Text(text = stringResource(R.string.library_manage_books_remove))
        }
      }

      false -> {
        Button(
          onClick = onAction,
          enabled = enabled,
        ) {
          Text(text = stringResource(R.string.library_manage_books_add))
        }
      }
    }
  }
}
