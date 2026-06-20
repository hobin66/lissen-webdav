package io.github.hobin66.webdavplayer.channel.common

import android.content.Context
import io.github.hobin66.webdavplayer.R

sealed class OperationError {
  data object Unauthorized : OperationError()

  data object NetworkError : OperationError()

  data object InvalidCredentialsHost : OperationError()

  data object MissingCredentialsHost : OperationError()

  data object MissingCredentialsUsername : OperationError()

  data object MissingCredentialsPassword : OperationError()

  data object MissingCredentialsRootPath : OperationError()

  data object InternalError : OperationError()

  data object NotFoundError : OperationError()

  data object ConflictError : OperationError()

  data object UnsupportedError : OperationError()
}

fun OperationError.makeText(context: Context) =
  when (this) {
    OperationError.InternalError -> {
      context.getString(R.string.login_error_host_is_down)
    }

    OperationError.MissingCredentialsHost -> {
      context.getString(R.string.login_error_host_url_is_missing)
    }

    OperationError.MissingCredentialsPassword -> {
      context.getString(R.string.login_error_password_is_missing)
    }

    OperationError.MissingCredentialsUsername -> {
      context.getString(R.string.login_error_username_is_missing)
    }

    OperationError.MissingCredentialsRootPath -> {
      context.getString(R.string.login_error_root_path_is_missing)
    }

    OperationError.Unauthorized -> {
      context.getString(R.string.login_error_credentials_are_invalid)
    }

    OperationError.InvalidCredentialsHost -> {
      context.getString(R.string.login_error_host_url_shall_be_https_or_http)
    }

    OperationError.NetworkError -> {
      context.getString(R.string.login_error_connection_error)
    }

    OperationError.UnsupportedError -> {
      context.getString(R.string.login_error_connection_error)
    }

    OperationError.NotFoundError -> {
      context.getString(R.string.login_error_server_not_found)
    }

    OperationError.ConflictError -> {
      context.getString(R.string.login_error_metadata_conflict)
    }
  }
