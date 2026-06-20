package org.grakovne.lissen.buildlogic

import java.io.File

private val shortGitHashPattern = Regex("^[0-9a-fA-F]{7,40}$")
private val releaseArtifactTaskNames =
  listOf(
    "assemblerelease",
    "bundlerelease",
    "packagerelease",
    "publishrelease",
    "installrelease",
    "validatesigningrelease",
  )

data class GitCommandResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
)

fun runGitShortHead(projectDir: File): GitCommandResult =
  try {
    val process =
      ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(projectDir)
        .start()

    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val stderr = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    GitCommandResult(
      exitCode = exitCode,
      stdout = stdout,
      stderr = stderr,
    )
  } catch (exception: Exception) {
    GitCommandResult(
      exitCode = -1,
      stdout = "",
      stderr = exception.message.orEmpty(),
    )
  }

fun resolveGitHash(
  result: GitCommandResult,
  fallback: String = "stable",
): String {
  if (result.exitCode != 0) {
    return fallback
  }

  val candidate = result.stdout.trim()
  if (!candidate.matches(shortGitHashPattern)) {
    return fallback
  }

  return candidate
}

fun missingRequiredProperties(
  propertyNames: List<String>,
  lookup: (String) -> String?,
): List<String> =
  propertyNames.filter { propertyName ->
    lookup(propertyName).isNullOrBlank()
  }

fun requiresReleaseSigning(taskNames: List<String>): Boolean =
  taskNames
    .map { it.lowercase() }
    .any { normalizedTaskName ->
      releaseArtifactTaskNames.any(normalizedTaskName::contains)
    }
