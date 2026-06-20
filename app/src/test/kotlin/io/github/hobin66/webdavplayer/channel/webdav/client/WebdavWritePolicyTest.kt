package io.github.hobin66.webdavplayer.channel.webdav.client

import io.github.hobin66.webdavplayer.channel.common.OperationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebdavWritePolicyTest {
  @Test
  fun `returns null if match header when etag is missing`() {
    assertNull(buildIfMatchHeader(null))
    assertNull(buildIfMatchHeader(""))
    assertNull(buildIfMatchHeader("   "))
  }

  @Test
  fun `returns if match header value when etag is present`() {
    assertEquals("\"etag-value\"", buildIfMatchHeader("\"etag-value\""))
  }

  @Test
  fun `does not return if match header for weak etag`() {
    assertNull(buildIfMatchHeader("W/\"etag-value\""))
    assertNull(buildIfMatchHeader("  W/\"etag-value\"  "))
  }

  @Test
  fun `prefers etag overwrite condition when etag and last modified are present`() {
    val headers =
      buildOverwriteConditionHeaders(
        knownEtag = "\"etag-value\"",
        knownLastModified = "Mon, 01 Jan 2024 00:00:00 GMT",
      )

    assertEquals("\"etag-value\"", headers?.ifMatch)
    assertNull(headers?.ifUnmodifiedSince)
  }

  @Test
  fun `uses last modified overwrite condition when etag is missing`() {
    val headers =
      buildOverwriteConditionHeaders(
        knownEtag = null,
        knownLastModified = "Mon, 01 Jan 2024 00:00:00 GMT",
      )

    assertNull(headers?.ifMatch)
    assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", headers?.ifUnmodifiedSince)
  }

  @Test
  fun `uses last modified overwrite condition when etag is weak`() {
    val headers =
      buildOverwriteConditionHeaders(
        knownEtag = "W/\"etag-value\"",
        knownLastModified = "Mon, 01 Jan 2024 00:00:00 GMT",
      )

    assertNull(headers?.ifMatch)
    assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", headers?.ifUnmodifiedSince)
  }

  @Test
  fun `does not build overwrite condition when etag and last modified are missing`() {
    assertNull(buildOverwriteConditionHeaders(knownEtag = null, knownLastModified = null))
    assertNull(buildOverwriteConditionHeaders(knownEtag = " ", knownLastModified = " "))
  }

  @Test
  fun `maps overwrite conflict response to conflict error`() {
    assertEquals(OperationError.ConflictError, mapOverwriteResponseCode(412))
  }

  @Test
  fun `maps unauthorized overwrite response to unauthorized error`() {
    assertEquals(OperationError.Unauthorized, mapOverwriteResponseCode(401))
    assertEquals(OperationError.Unauthorized, mapOverwriteResponseCode(403))
  }

  @Test
  fun `maps missing file overwrite response to not found error`() {
    assertEquals(OperationError.NotFoundError, mapOverwriteResponseCode(404))
  }

  @Test
  fun `maps unknown overwrite response to network error`() {
    assertEquals(OperationError.NetworkError, mapOverwriteResponseCode(500))
  }

  @Test
  fun `maps non precondition overwrite conflicts to network error`() {
    assertEquals(OperationError.NetworkError, mapOverwriteResponseCode(409))
  }

  @Test
  fun `put text accepts only single resource success codes`() {
    assertEquals(true, isSuccessfulPutTextStatusCode(200))
    assertEquals(true, isSuccessfulPutTextStatusCode(201))
    assertEquals(true, isSuccessfulPutTextStatusCode(204))
    assertEquals(false, isSuccessfulPutTextStatusCode(207))
  }
}
