package io.github.hobin66.webdavplayer.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.channel.common.OperationError
import io.github.hobin66.webdavplayer.channel.common.OperationError.MissingCredentialsHost
import io.github.hobin66.webdavplayer.channel.common.OperationError.MissingCredentialsPassword
import io.github.hobin66.webdavplayer.channel.common.OperationError.MissingCredentialsRootPath
import io.github.hobin66.webdavplayer.channel.common.OperationError.MissingCredentialsUsername
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
  @Inject
  constructor(
    preferences: WebdavPlayerPreferences,
    private val mediaChannel: WebdavMediaProvider,
  ) : ViewModel() {
    private val _host = MutableLiveData(preferences.getHost() ?: "")
    val host = _host

    private val _username = MutableLiveData(preferences.getUsername() ?: "")
    val username = _username

    private val _password = MutableLiveData(preferences.getPassword() ?: "")
    val password = _password

    private val _rootPath = MutableLiveData(preferences.getWebdavRoot() ?: "/")
    val rootPath = _rootPath

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun setHost(host: String) {
      _host.value = host
    }

    fun setUsername(username: String) {
      _username.value = username
    }

    fun setPassword(password: String) {
      _password.value = password
    }

    fun setRootPath(rootPath: String) {
      _rootPath.value = rootPath
    }

    fun readyToLogin() {
      _loginState.value = LoginState.Idle
    }

    fun login() {
      viewModelScope.launch {
        _loginState.value = LoginState.Loading

        val host =
          host.value ?: run {
            _loginState.value = LoginState.Error(MissingCredentialsHost)
            return@launch
          }

        val username =
          username.value ?: run {
            _loginState.value = LoginState.Error(MissingCredentialsUsername)
            return@launch
          }

        val password =
          password.value ?: run {
            _loginState.value = LoginState.Error(MissingCredentialsPassword)
            return@launch
          }

        val rootDirectory =
          rootPath.value?.takeIf { it.isNotBlank() } ?: run {
            _loginState.value = LoginState.Error(MissingCredentialsRootPath)
            return@launch
          }

        val result =
          mediaChannel
            .authorize(host, username, password, rootDirectory)
            .foldAsync(
              onSuccess = { _ -> LoginState.Success },
              onFailure = { error -> onLoginFailure(error.code) },
            )
        _loginState.value = result
      }
    }

    private fun onLoginFailure(error: OperationError): LoginState.Error {
      viewModelScope.launch {
        _loginState.value = LoginState.Error(error)
      }
      return LoginState.Error(error)
    }

    sealed class LoginState {
      data object Idle : LoginState()

      data object Loading : LoginState()

      data object Success : LoginState()

      data class Error(
        val message: OperationError,
      ) : LoginState()
    }
  }
