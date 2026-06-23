package io.github.hobin66.webdavplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.channel.common.ConnectionHost
import io.github.hobin66.webdavplayer.channel.common.OperationResult
import io.github.hobin66.webdavplayer.channel.webdav.WebdavRefreshProgress
import io.github.hobin66.webdavplayer.common.ColorScheme
import io.github.hobin66.webdavplayer.common.LibraryOrderingConfiguration
import io.github.hobin66.webdavplayer.common.NetworkTypeAutoCache
import io.github.hobin66.webdavplayer.common.PlaybackVolumeBoost
import io.github.hobin66.webdavplayer.content.PlaybackProgressSyncDirection
import io.github.hobin66.webdavplayer.content.WebdavMediaProvider
import io.github.hobin66.webdavplayer.lib.domain.DownloadOption
import io.github.hobin66.webdavplayer.lib.domain.Library
import io.github.hobin66.webdavplayer.lib.domain.SeekTimeOption
import io.github.hobin66.webdavplayer.persistence.preferences.WebdavPlayerPreferences
import io.github.hobin66.webdavplayer.playback.MediaCodecQueueingMode
import io.github.hobin66.webdavplayer.session.SessionResetCoordinator
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
  @Inject
  constructor(
    private val mediaChannel: WebdavMediaProvider,
    private val preferences: WebdavPlayerPreferences,
    private val sessionResetCoordinator: SessionResetCoordinator,
  ) : ViewModel() {
    private val _host: MutableLiveData<ConnectionHost> =
      MutableLiveData(preferences.getHost()?.let { ConnectionHost.external(it) })
    val host = _host

    private val _serverVersion = MutableLiveData(preferences.getServerVersion())
    val serverVersion = _serverVersion

    private val _username = MutableLiveData(preferences.getUsername())
    val username = _username

    private val _rootPath = MutableLiveData(preferences.getWebdavRoot())
    val rootPath = _rootPath

    private val _password = MutableLiveData(preferences.getPassword())
    val password = _password

    private val _preferredLibrary = MutableLiveData<Library?>(preferences.getPreferredLibrary())
    val preferredLibrary: LiveData<Library?> = _preferredLibrary

    private val _preferredColorScheme = MutableLiveData(preferences.getColorScheme())
    val preferredColorScheme = _preferredColorScheme

    private val _materialYouEnabled = MutableLiveData(preferences.getMaterialYouColors())
    val materialYouEnabled = _materialYouEnabled

    private val _preferredAutoDownloadNetworkType = MutableLiveData(preferences.getAutoDownloadNetworkType())
    val preferredAutoDownloadNetworkType = _preferredAutoDownloadNetworkType

    private val _preferredAutoDownloadOption = MutableLiveData(preferences.getAutoDownloadOption())
    val preferredAutoDownloadOption = _preferredAutoDownloadOption

    private val _preferredPlaybackVolumeBoost = MutableLiveData(preferences.getPlaybackVolumeBoost())
    val preferredPlaybackVolumeBoost = _preferredPlaybackVolumeBoost

    private val _preferredLibraryOrdering = MutableLiveData(preferences.getLibraryOrdering())
    val preferredLibraryOrdering: LiveData<LibraryOrderingConfiguration> = _preferredLibraryOrdering

    private val _seekTime = MutableLiveData(preferences.getSeekTime())
    val seekTime = _seekTime

    private val _softwareCodecsEnabled = MutableLiveData(preferences.getSoftwareCodecsEnabled())

    val softwareCodecsEnabled: LiveData<Boolean> = _softwareCodecsEnabled
    val softwareCodecsEnabledOnStart: Boolean = preferences.getSoftwareCodecsEnabled()

    private val _mediaCodecQueueingMode = MutableLiveData(preferences.getMediaCodecQueueingMode())
    val mediaCodecQueueingMode: LiveData<MediaCodecQueueingMode> = _mediaCodecQueueingMode
    val mediaCodecQueueingModeOnStart: MediaCodecQueueingMode = preferences.getMediaCodecQueueingMode()

    private val _hideCompleted = preferences.hideCompletedFlow
    val hideCompleted = _hideCompleted

    private val _autoDownloadDelayed = MutableLiveData(preferences.getAutoDownloadDelayed())
    val autoDownloadDelayed = _autoDownloadDelayed

    private val _webdavRefreshInProgress = MutableLiveData(false)
    val webdavRefreshInProgress: LiveData<Boolean> = _webdavRefreshInProgress

    private val _webdavRefreshProgress = MutableLiveData<WebdavRefreshProgress?>(null)
    val webdavRefreshProgress: LiveData<WebdavRefreshProgress?> = _webdavRefreshProgress

    private val _webdavRefreshMessageRes = MutableLiveData<Int?>(null)
    val webdavRefreshMessageRes: LiveData<Int?> = _webdavRefreshMessageRes

    fun preferAutoDownloadDelayed(value: Boolean) {
      _autoDownloadDelayed.postValue(value)
      preferences.saveAutoDownloadDelayed(value)
    }

    fun toggleHideCompleted() {
      when (preferences.getHideCompleted()) {
        true -> preferences.saveHideCompleted(false)
        false -> preferences.saveHideCompleted(true)
      }
    }

    fun logout(onComplete: () -> Unit = {}) {
      viewModelScope.launch {
        sessionResetCoordinator.logout()
        onComplete()
      }
    }

    fun refreshWebdavCache() {
      if (_webdavRefreshInProgress.value == true) {
        return
      }

      viewModelScope.launch {
        _webdavRefreshInProgress.postValue(true)
        _webdavRefreshProgress.postValue(WebdavRefreshProgress.start(totalBooks = 0))

        when (
          val result =
            mediaChannel.refreshRemoteCache { progress ->
              _webdavRefreshProgress.postValue(progress)
            }
        ) {
          is OperationResult.Success<*> -> {
            fetchLibraries()
          }

          is OperationResult.Error<*> -> {
            _webdavRefreshMessageRes.postValue(R.string.settings_refresh_webdav_cache_failed)
          }
        }

        _webdavRefreshInProgress.postValue(false)
        _webdavRefreshProgress.postValue(null)
      }
    }

    fun syncPlaybackProgress(direction: PlaybackProgressSyncDirection) {
      if (_webdavRefreshInProgress.value == true) {
        return
      }

      viewModelScope.launch {
        _webdavRefreshInProgress.postValue(true)
        _webdavRefreshProgress.postValue(WebdavRefreshProgress.start(totalBooks = 0))

        when (
          val result =
            mediaChannel.syncPlaybackProgress(direction) { progress ->
              _webdavRefreshProgress.postValue(progress)
            }
        ) {
          is OperationResult.Success<*> -> {
            _webdavRefreshMessageRes.postValue(R.string.settings_sync_playback_progress_success)
            fetchLibraries()
          }

          is OperationResult.Error<*> -> {
            _webdavRefreshMessageRes.postValue(R.string.settings_sync_playback_progress_failed)
          }
        }

        _webdavRefreshInProgress.postValue(false)
        _webdavRefreshProgress.postValue(null)
      }
    }

    fun refreshWebdavItemCache(itemId: String) {
      viewModelScope.launch {
        mediaChannel.refreshItemCache(itemId)
      }
    }

    fun canRefreshWebdavCache(): Boolean = mediaChannel.canRefreshRemoteCache()

    fun canSyncPlaybackProgress(): Boolean = mediaChannel.canSyncPlaybackProgress()

    fun consumeWebdavRefreshMessage() {
      _webdavRefreshMessageRes.postValue(null)
    }

    fun refreshConnectionInfo() {
      fetchConnectionHost()
      _username.postValue(preferences.getUsername())
      _password.postValue(preferences.getPassword())
      _rootPath.postValue(preferences.getWebdavRoot())
      _serverVersion.postValue(preferences.getServerVersion())

      viewModelScope.launch {
        when (val response = mediaChannel.fetchConnectionInfo()) {
          is OperationResult.Error -> {
            Unit
          }

          is OperationResult.Success -> {
            _username.postValue(response.data.username)
            _serverVersion.postValue(response.data.serverVersion)

            cacheServerInfo()
          }
        }
      }
    }

    fun fetchLibraries() {
      viewModelScope.launch {
        when (val response = mediaChannel.fetchLibraries()) {
          is OperationResult.Success -> {
            val libraries = response.data

            val preferredLibrary = preferences.getPreferredLibrary()
            val selectedLibrary =
              when (preferredLibrary) {
                null -> libraries.firstOrNull()
                else -> libraries.find { it.id == preferredLibrary.id }
              }

            _preferredLibrary.postValue(selectedLibrary)
            selectedLibrary?.let { preferences.savePreferredLibrary(it) }
          }

          is OperationResult.Error -> {
            _preferredLibrary.postValue(preferences.getPreferredLibrary())
          }
        }
      }
    }

    fun fetchPreferredLibraryId(): String = preferences.getPreferredLibrary()?.id ?: ""

    fun fetchLibraryOrdering(): LibraryOrderingConfiguration = preferences.getLibraryOrdering()

    fun preferLibrary(library: Library) {
      _preferredLibrary.postValue(library)
      preferences.savePreferredLibrary(library)
    }

    fun preferAutoDownloadNetworkType(type: NetworkTypeAutoCache) {
      _preferredAutoDownloadNetworkType.postValue(type)
      preferences.saveAutoDownloadNetworkType(type)
    }

    fun preferLibraryOrdering(configuration: LibraryOrderingConfiguration) {
      _preferredLibraryOrdering.postValue(configuration)
      preferences.saveLibraryOrdering(configuration)
    }

    fun preferPlaybackVolumeBoost(playbackVolumeBoost: PlaybackVolumeBoost) {
      _preferredPlaybackVolumeBoost.postValue(playbackVolumeBoost)
      preferences.savePlaybackVolumeBoost(playbackVolumeBoost)
    }

    fun preferColorScheme(colorScheme: ColorScheme) {
      _preferredColorScheme.postValue(colorScheme)
      preferences.saveColorScheme(colorScheme)
    }

    fun preferMaterialYouColors(value: Boolean) {
      _materialYouEnabled.postValue(value)
      preferences.saveMaterialYouColors(value)
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      _softwareCodecsEnabled.postValue(value)
      preferences.saveSoftwareCodecsEnabled(value)
    }

    fun preferMediaCodecQueueingMode(value: MediaCodecQueueingMode) {
      _mediaCodecQueueingMode.postValue(value)
      preferences.saveMediaCodecQueueingMode(value)
    }

    fun preferAutoDownloadOption(option: DownloadOption?) {
      _preferredAutoDownloadOption.postValue(option)
      preferences.saveAutoDownloadOption(option)
    }

    fun preferForwardRewind(option: SeekTimeOption) {
      val current = _seekTime.value ?: return
      val updated = current.copy(forward = option)

      preferences.saveSeekTime(updated)
      _seekTime.postValue(updated)
    }

    fun preferRewindRewind(option: SeekTimeOption) {
      val current = _seekTime.value ?: return
      val updated = current.copy(rewind = option)

      preferences.saveSeekTime(updated)
      _seekTime.postValue(updated)
    }

    fun hasCredentials() = preferences.hasCredentials()

    private fun cacheServerInfo() {
      serverVersion.value?.let { preferences.saveServerVersion(it) }
      username.value?.let { preferences.saveUsername(it) }
    }

    private fun fetchConnectionHost() {
      val host =
        when (val response = mediaChannel.fetchConnectionHost()) {
          is OperationResult.Error -> {
            preferences.getHost()?.let { ConnectionHost.external(it) }
          }

          is OperationResult.Success -> {
            response.data
          }
        }

      host?.let { _host.postValue(it) }
    }
  }
