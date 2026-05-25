package org.grakovne.lissen.channel.webdav.client

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.grakovne.lissen.channel.common.AuthOverride
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.channel.webdav.model.WebdavResource
import org.grakovne.lissen.channel.webdav.toWebdavRootRelativePath
import org.grakovne.lissen.lib.domain.fixUriScheme
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ConditionalFetchStatus {
  UPDATED,
  NOT_MODIFIED,
  NOT_FOUND,
}

data class ConditionalTextResponse(
  val status: ConditionalFetchStatus,
  val content: String?,
  val eTag: String?,
  val lastModified: String?,
)

data class PutTextResponse(
  val eTag: String?,
  val lastModified: String?,
)

@Singleton
class WebdavClient
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
  ) {
    suspend fun checkRootAvailability(
      host: String,
      rootPath: String,
      username: String,
      password: String,
    ): OperationResult<Unit> {
      val override =
        AuthOverride(
          token = password,
          username = username,
          webdavRoot = rootPath,
        )
      return listResourcesInternal(
        host = host,
        rootPath = rootPath,
        relativePath = "",
        depth = 1,
        authOverride = override,
      ).foldAsync(
        onSuccess = { OperationResult.Success(Unit) },
        onFailure = { OperationResult.Error(it.code) },
      )
    }

    suspend fun listResources(
      relativePath: String,
      depth: Int = 1,
    ): OperationResult<List<WebdavResource>> =
      listResourcesInternal(
        host = preferences.getHost(),
        rootPath = preferences.getWebdavRoot() ?: "/",
        relativePath = relativePath,
        depth = depth,
        authOverride = null,
      )

    private suspend fun listResourcesInternal(
      host: String?,
      rootPath: String,
      relativePath: String,
      depth: Int,
      authOverride: AuthOverride?,
    ): OperationResult<List<WebdavResource>> =
      withContext(Dispatchers.IO) {
        val safeHost = host ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)

        val requestUri =
          resolveUri(
            host = safeHost,
            rootPath = rootPath,
            relativePath = relativePath,
          ) ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)

        val request =
          Request
            .Builder()
            .url(requestUri.toString())
            .header("Depth", depth.toString())
            .header("Content-Type", "application/xml")
            .method(
              "PROPFIND",
              PROPFIND_REQUEST_BODY.toRequestBody("application/xml".toMediaType()),
            ).build()

        executeRequest(request, authOverride).foldAsync(
          onSuccess = { response ->
            response.use {
              if (it.code !in successCodes) {
                return@foldAsync OperationResult.Error(mapResponseCode(it.code))
              }

              val responseBody = it.body?.string().orEmpty()
              if (responseBody.isBlank()) {
                return@foldAsync OperationResult.Success(emptyList())
              }

              val rootAbsolutePath =
                resolveUri(
                  host = safeHost,
                  rootPath = rootPath,
                  relativePath = "",
                )?.path.orEmpty()
              val resources =
                WebdavXmlParser
                  .parseMultistatus(responseBody)
                  .mapNotNull { parsed ->
                    val absolutePath = normalizeHrefToAbsolutePath(parsed.href)
                    val relative = toWebdavRootRelativePath(absolutePath, rootAbsolutePath) ?: return@mapNotNull null

                    if (relative.isBlank()) {
                      return@mapNotNull null
                    }

                    WebdavResource(
                      relativePath = relative,
                      name = relative.substringAfterLast('/'),
                      isDirectory = parsed.isDirectory,
                      eTag = parsed.eTag,
                      lastModified = parsed.lastModified,
                      size = parsed.size,
                      mimeType = parsed.mimeType,
                    )
                  }

              OperationResult.Success(resources)
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    suspend fun readTextConditionally(
      relativePath: String,
      knownEtag: String?,
      knownLastModified: String?,
    ): OperationResult<ConditionalTextResponse> =
      withContext(Dispatchers.IO) {
        val safeHost = preferences.getHost() ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)
        val rootPath = preferences.getWebdavRoot() ?: "/"

        val uri =
          resolveUri(host = safeHost, rootPath = rootPath, relativePath = relativePath)
            ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)

        val request =
          Request
            .Builder()
            .url(uri.toString())
            .apply {
              knownEtag?.takeIf { it.isNotBlank() }?.let { header("If-None-Match", it) }
              knownLastModified?.takeIf { it.isNotBlank() }?.let { header("If-Modified-Since", it) }
            }.get()
            .build()

        executeRequest(request, authOverride = null).foldAsync(
          onSuccess = { response ->
            response.use {
              when (it.code) {
                304 -> {
                  OperationResult.Success(
                    ConditionalTextResponse(
                      status = ConditionalFetchStatus.NOT_MODIFIED,
                      content = null,
                      eTag = findHeaderIgnoreCase(it.headers.toMultimap(), "ETag") ?: knownEtag,
                      lastModified = findHeaderIgnoreCase(it.headers.toMultimap(), "Last-Modified") ?: knownLastModified,
                    ),
                  )
                }

                404 -> {
                  OperationResult.Success(
                    ConditionalTextResponse(
                      status = ConditionalFetchStatus.NOT_FOUND,
                      content = null,
                      eTag = null,
                      lastModified = null,
                    ),
                  )
                }

                in successCodes -> {
                  OperationResult.Success(
                    ConditionalTextResponse(
                      status = ConditionalFetchStatus.UPDATED,
                      content = it.body.string(),
                      eTag = findHeaderIgnoreCase(it.headers.toMultimap(), "ETag"),
                      lastModified = findHeaderIgnoreCase(it.headers.toMultimap(), "Last-Modified"),
                    ),
                  )
                }

                else -> {
                  OperationResult.Error(mapResponseCode(it.code))
                }
              }
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    suspend fun readText(relativePath: String): OperationResult<String> =
      withContext(Dispatchers.IO) {
        val safeHost = preferences.getHost() ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)
        val rootPath = preferences.getWebdavRoot() ?: "/"

        val uri =
          resolveUri(host = safeHost, rootPath = rootPath, relativePath = relativePath)
            ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)

        val request =
          Request
            .Builder()
            .url(uri.toString())
            .get()
            .build()

        executeRequest(request, authOverride = null).foldAsync(
          onSuccess = { response ->
            response.use {
              if (it.code !in successCodes) {
                return@foldAsync OperationResult.Error(mapResponseCode(it.code))
              }
              OperationResult.Success(it.body?.string().orEmpty())
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    suspend fun putTextIfAbsent(
      relativePath: String,
      content: String,
    ): OperationResult<Unit> =
      withContext(Dispatchers.IO) {
        val safeHost = preferences.getHost() ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)
        val rootPath = preferences.getWebdavRoot() ?: "/"

        val uri =
          resolveUri(host = safeHost, rootPath = rootPath, relativePath = relativePath)
            ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)

        val request =
          Request
            .Builder()
            .url(uri.toString())
            .header("If-None-Match", "*")
            .put(content.toRequestBody("application/json".toMediaType()))
            .build()

        executeRequest(request, authOverride = null).foldAsync(
          onSuccess = { response ->
            response.use {
              when (it.code) {
                200, 201, 204, 412 -> OperationResult.Success(Unit)
                else -> OperationResult.Error(mapResponseCode(it.code))
              }
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    suspend fun putText(
      relativePath: String,
      content: String,
      knownEtag: String? = null,
      knownLastModified: String? = null,
    ): OperationResult<PutTextResponse> =
      withContext(Dispatchers.IO) {
        val safeHost = preferences.getHost() ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)
        val rootPath = preferences.getWebdavRoot() ?: "/"

        val uri =
          resolveUri(host = safeHost, rootPath = rootPath, relativePath = relativePath)
            ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)
        val conditionHeaders =
          buildOverwriteConditionHeaders(
            knownEtag = knownEtag,
            knownLastModified = knownLastModified,
          ) ?: return@withContext OperationResult.Error(OperationError.ConflictError)

        val request =
          Request
            .Builder()
            .url(uri.toString())
            .apply {
              conditionHeaders.ifMatch?.let { header("If-Match", it) }
              conditionHeaders.ifUnmodifiedSince?.let { header("If-Unmodified-Since", it) }
            }.put(content.toRequestBody("application/json".toMediaType()))
            .build()

        executeRequest(request, authOverride = null).foldAsync(
          onSuccess = { response ->
            response.use {
              if (!isSuccessfulPutTextStatusCode(it.code)) {
                return@foldAsync OperationResult.Error(mapOverwriteResponseCode(it.code))
              }

              OperationResult.Success(
                PutTextResponse(
                  eTag = findHeaderIgnoreCase(it.headers.toMultimap(), "ETag"),
                  lastModified = findHeaderIgnoreCase(it.headers.toMultimap(), "Last-Modified"),
                ),
              )
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    suspend fun head(relativePath: String): OperationResult<Map<String, String>> =
      withContext(Dispatchers.IO) {
        val safeHost = preferences.getHost() ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)
        val rootPath = preferences.getWebdavRoot() ?: "/"

        val uri =
          resolveUri(host = safeHost, rootPath = rootPath, relativePath = relativePath)
            ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)

        val request =
          Request
            .Builder()
            .url(uri.toString())
            .head()
            .build()

        executeRequest(request, authOverride = null).foldAsync(
          onSuccess = { response ->
            response.use {
              if (it.code !in successCodes) {
                return@foldAsync OperationResult.Error(mapResponseCode(it.code))
              }

              OperationResult.Success(
                it.headers.toMultimap().mapValues { (_, value) -> value.firstOrNull().orEmpty() },
              )
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    suspend fun fetchBinary(relativePath: String): OperationResult<Buffer> =
      withContext(Dispatchers.IO) {
        val safeHost = preferences.getHost() ?: return@withContext OperationResult.Error(OperationError.MissingCredentialsHost)
        val rootPath = preferences.getWebdavRoot() ?: "/"

        val uri =
          resolveUri(host = safeHost, rootPath = rootPath, relativePath = relativePath)
            ?: return@withContext OperationResult.Error(OperationError.InvalidCredentialsHost)

        val request =
          Request
            .Builder()
            .url(uri.toString())
            .get()
            .build()

        executeRequest(request, authOverride = null).foldAsync(
          onSuccess = { response ->
            response.use {
              if (it.code !in successCodes) {
                return@foldAsync OperationResult.Error(mapResponseCode(it.code))
              }

              val body = it.body ?: return@foldAsync OperationResult.Error(OperationError.InternalError)
              OperationResult.Success(Buffer().apply { writeAll(body.source()) })
            }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    fun resolveUri(relativePath: String): Uri? =
      resolveUri(
        host = preferences.getHost() ?: return null,
        rootPath = preferences.getWebdavRoot() ?: "/",
        relativePath = relativePath,
      )

    fun resolveUri(
      host: String,
      rootPath: String,
      relativePath: String,
    ): Uri? =
      runCatching {
        val base = host.fixUriScheme().toUri()
        val builder =
          Uri
            .Builder()
            .scheme(base.scheme)
            .encodedAuthority(base.encodedAuthority)

        base.pathSegments
          .filter { it.isNotBlank() }
          .forEach(builder::appendPath)

        normalizePath(rootPath)
          .split("/")
          .filter { it.isNotBlank() }
          .forEach(builder::appendPath)

        normalizePath(relativePath)
          .split("/")
          .filter { it.isNotBlank() }
          .forEach(builder::appendPath)

        builder.build()
      }.onFailure { Timber.w(it, "Failed to resolve WebDAV uri for $host + $rootPath + $relativePath") }
        .getOrNull()

    private fun executeRequest(
      request: Request,
      authOverride: AuthOverride?,
    ): OperationResult<okhttp3.Response> =
      runCatching {
        val client = createOkHttpClient(preferences = preferences, authOverride = authOverride)
        client.newCall(request).execute()
      }.fold(
        onSuccess = { OperationResult.Success(it) },
        onFailure = {
          Timber.e(it, "WebDAV request failed: ${request.method} ${request.url}")
          OperationResult.Error(OperationError.NetworkError)
        },
      )

    private fun normalizeHrefToAbsolutePath(href: String): String = normalizeWebdavHrefToAbsolutePath(href)

    private fun findHeaderIgnoreCase(
      headers: Map<String, List<String>>,
      expectedName: String,
    ): String? =
      headers
        .entries
        .firstOrNull { (name, _) -> name.equals(expectedName, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    private fun mapResponseCode(code: Int): OperationError =
      when (code) {
        401, 403 -> OperationError.Unauthorized
        404 -> OperationError.NotFoundError
        else -> OperationError.NetworkError
      }

    private fun normalizePath(path: String): String =
      path
        .replace('\\', '/')
        .trim()
        .trim('/')

    companion object {
      private val successCodes = setOf(200, 201, 204, 207)

      private val PROPFIND_REQUEST_BODY =
        """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:">
          <d:prop>
            <d:resourcetype />
            <d:getlastmodified />
            <d:getetag />
            <d:getcontenttype />
            <d:getcontentlength />
          </d:prop>
        </d:propfind>
        """.trimIndent()
    }
  }

internal fun normalizeWebdavHrefToAbsolutePath(href: String): String {
  val hrefPath = extractWebdavHrefPath(href)
  val decodedBytes = ByteArrayOutputStream()
  val normalized = StringBuilder(hrefPath.length)
  var index = 0

  fun flushDecodedBytes() {
    if (decodedBytes.size() == 0) {
      return
    }

    normalized.append(decodedBytes.toString(Charsets.UTF_8.name()))
    decodedBytes.reset()
  }

  while (index < hrefPath.length) {
    val current = hrefPath[index]
    if (current == '%' && index + 2 < hrefPath.length) {
      val hi = hrefPath[index + 1].digitToIntOrNull(16)
      val lo = hrefPath[index + 2].digitToIntOrNull(16)

      if (hi != null && lo != null) {
        decodedBytes.write((hi shl 4) + lo)
        index += 3
        continue
      }
    }

    flushDecodedBytes()
    normalized.append(current)
    index++
  }

  flushDecodedBytes()
  return normalized.toString()
}

private fun extractWebdavHrefPath(href: String): String {
  val trimmed = href.trim()
  if (trimmed.isBlank()) {
    return trimmed
  }

  val schemeSeparator = trimmed.indexOf("://")
  val pathWithOptionalQuery =
    if (schemeSeparator >= 0) {
      val pathStart = trimmed.indexOf('/', startIndex = schemeSeparator + 3)
      if (pathStart >= 0) trimmed.substring(pathStart) else "/"
    } else {
      trimmed
    }

  val queryOrFragmentStart =
    pathWithOptionalQuery
      .indexOfFirst { it == '?' || it == '#' }
      .takeIf { it >= 0 }
      ?: pathWithOptionalQuery.length

  return pathWithOptionalQuery.substring(0, queryOrFragmentStart)
}
