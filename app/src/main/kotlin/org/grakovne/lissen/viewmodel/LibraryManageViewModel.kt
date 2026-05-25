package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.webdav.WebdavManageBookItem
import org.grakovne.lissen.content.LissenMediaProvider
import javax.inject.Inject

@HiltViewModel
class LibraryManageViewModel
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
  ) : ViewModel() {
    private val _books = MutableLiveData<List<WebdavManageBookItem>>(emptyList())
    val books: LiveData<List<WebdavManageBookItem>> = _books

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _updatingBookIds = MutableLiveData<Set<String>>(emptySet())
    val updatingBookIds: LiveData<Set<String>> = _updatingBookIds

    private val _messageRes = MutableLiveData<Int?>(null)
    val messageRes: LiveData<Int?> = _messageRes

    fun loadBooks(forceRefresh: Boolean = false) {
      if (_loading.value == true && !forceRefresh) {
        return
      }

      viewModelScope.launch {
        _loading.postValue(true)
        when (val result = mediaProvider.fetchManageBooks(forceRefresh)) {
          is OperationResult.Success -> {
            _books.postValue(result.data)
          }

          is OperationResult.Error -> {
            _messageRes.postValue(R.string.library_manage_books_load_failed)
          }
        }
        _loading.postValue(false)
      }
    }

    fun toggleBook(item: WebdavManageBookItem) {
      val currentlyUpdating = _updatingBookIds.value ?: emptySet()
      if (item.id in currentlyUpdating) {
        return
      }

      viewModelScope.launch {
        _updatingBookIds.postValue(currentlyUpdating + item.id)

        val result =
          when (item.isAdded) {
            true -> mediaProvider.removeBookFromLibrary(item.id)
            false -> mediaProvider.addBookToLibrary(item.id)
          }

        when (result) {
          is OperationResult.Success -> {
            _books.postValue(
              _books.value
                ?.map { current ->
                  if (current.id == item.id) {
                    current.copy(isAdded = !current.isAdded)
                  } else {
                    current
                  }
                },
            )
          }

          is OperationResult.Error -> {
            _messageRes.postValue(R.string.library_manage_books_update_failed)
          }
        }

        _updatingBookIds.postValue((_updatingBookIds.value ?: emptySet()) - item.id)
      }
    }

    fun consumeMessage() {
      _messageRes.postValue(null)
    }
  }
