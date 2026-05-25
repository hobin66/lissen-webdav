package org.grakovne.lissen.channel.webdav.client

import org.grakovne.lissen.channel.common.OperationError

data class OverwriteConditionHeaders(
  val ifMatch: String?,
  val ifUnmodifiedSince: String?,
)

fun buildIfMatchHeader(knownEtag: String?): String? =
  knownEtag
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.takeUnless { it.startsWith("W/") }

fun buildIfUnmodifiedSinceHeader(knownLastModified: String?): String? = knownLastModified?.trim()?.takeIf { it.isNotBlank() }

fun buildOverwriteConditionHeaders(
  knownEtag: String?,
  knownLastModified: String?,
): OverwriteConditionHeaders? {
  buildIfMatchHeader(knownEtag)?.let {
    return OverwriteConditionHeaders(
      ifMatch = it,
      ifUnmodifiedSince = null,
    )
  }

  return buildIfUnmodifiedSinceHeader(knownLastModified)?.let {
    OverwriteConditionHeaders(
      ifMatch = null,
      ifUnmodifiedSince = it,
    )
  }
}

fun mapOverwriteResponseCode(code: Int): OperationError =
  when (code) {
    401, 403 -> OperationError.Unauthorized
    404 -> OperationError.NotFoundError
    412 -> OperationError.ConflictError
    else -> OperationError.NetworkError
  }

fun isSuccessfulPutTextStatusCode(code: Int): Boolean = code in putTextSuccessStatusCodes

private val putTextSuccessStatusCodes = setOf(200, 201, 204)
