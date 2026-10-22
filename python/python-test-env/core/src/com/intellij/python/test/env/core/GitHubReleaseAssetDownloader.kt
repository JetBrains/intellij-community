// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.util.io.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

private const val CACHE_REDIRECTOR_GITHUB_URL = "https://cache-redirector.jetbrains.com/github.com"
private const val GITHUB_API_URL = "https://api.github.com"
private const val RELEASE_METADATA_FILE_NAME = "github-release.json"
private val CHECKSUM_SEPARATOR = Regex("\\s+")
private val SHA256_PATTERN = Regex("[0-9a-f]{64}", RegexOption.IGNORE_CASE)
private val JSON_MAPPER = jacksonObjectMapper()

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubReleaseMetadata(@field:JsonProperty("assets") val assets: List<GitHubAssetMetadata> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubAssetMetadata(
  @field:JsonProperty("name") val name: String,
  @field:JsonProperty("digest") val digest: String? = null,
)

/** Downloads and verifies GitHub release assets from one repository through Cache Redirector. */
@ApiStatus.Internal
class GitHubReleaseAssetDownloader(
  private val repository: String,
  private val checksumResolver: GitHubReleaseChecksumResolver = GitHubReleaseChecksumResolver.None,
  private val cacheDownload: suspend (url: String, assetName: String) -> Path = defaultPyEnvDownloadCache::getOrDownload,
  private val validatedCacheDownload: suspend (url: String, assetName: String, isValid: (Path) -> Boolean) -> Path =
    { url, assetName, isValid -> defaultPyEnvDownloadCache.getOrDownload(url, assetName, isValid) },
) {
  /** Returns a cached or downloaded release asset. A configured checksum resolver verifies the asset. */
  suspend fun download(tag: String, assetName: String): Path = withContext(Dispatchers.IO) {
    checksumResolver.download(this@GitHubReleaseAssetDownloader, tag, assetName)
  }

  internal suspend fun downloadWithSha256(tag: String, assetName: String, expectedSha256: String): Path {
    val assetUrl = getAssetUrl(tag, assetName)
    return validatedCacheDownload(assetUrl, assetName) { hasSha256(it, expectedSha256) }
  }

  internal suspend fun findSha256(tag: String, checksumAssetName: String, assetName: String): String? =
    findSha256(Files.readAllLines(downloadCached(tag, checksumAssetName)), assetName)

  internal suspend fun findGitHubApiSha256(tag: String, assetName: String): String? {
    val metadataUrl = "$GITHUB_API_URL/repos/$repository/releases/tags/$tag"
    val metadataFile = cacheDownload(metadataUrl, RELEASE_METADATA_FILE_NAME)
    val metadata = Files.newInputStream(metadataFile).use { JSON_MAPPER.readValue(it, GitHubReleaseMetadata::class.java) }
    return metadata.assets.firstOrNull { it.name == assetName }?.digest
      ?.removePrefix("sha256:")
      ?.takeIf(SHA256_PATTERN::matches)
  }

  internal suspend fun downloadCached(tag: String, assetName: String): Path =
    cacheDownload(getAssetUrl(tag, assetName), assetName)

  private fun findSha256(checksumLines: List<String>, assetName: String): String? = checksumLines
    .map { it.trim().split(CHECKSUM_SEPARATOR, limit = 2) }
    .firstOrNull { it.size == 2 && it[1].removePrefix("*") == assetName }
    ?.first()
    ?.takeIf(SHA256_PATTERN::matches)

  private fun hasSha256(path: Path, expectedSha256: String): Boolean =
    sha256Hex(path).equals(expectedSha256, ignoreCase = true)

  private fun getAssetUrl(tag: String, assetName: String): String =
    "$CACHE_REDIRECTOR_GITHUB_URL/$repository/releases/download/$tag/$assetName"
}

/** Resolves checksums published with GitHub release assets. */
@ApiStatus.Internal
sealed class GitHubReleaseChecksumResolver {
  internal abstract suspend fun download(downloader: GitHubReleaseAssetDownloader, tag: String, assetName: String): Path

  /** Reads the digest included in GitHub release asset metadata. */
  data object GitHubApiDigest : GitHubReleaseChecksumResolver() {
    override suspend fun download(downloader: GitHubReleaseAssetDownloader, tag: String, assetName: String): Path {
      val expectedSha256 = downloader.findGitHubApiSha256(tag, assetName)
      if (expectedSha256 == null) {
        error("The GitHub API SHA-256 digest for $assetName is missing")
      }
      return downloader.downloadWithSha256(tag, assetName, expectedSha256)
    }
  }

  /** Reads checksums from a GNU checksum manifest release asset. */
  data class GnuManifest(val assetName: String) : GitHubReleaseChecksumResolver() {
    override suspend fun download(downloader: GitHubReleaseAssetDownloader, tag: String, assetName: String): Path {
      val expectedSha256 = downloader.findSha256(tag, this.assetName, assetName)
      if (expectedSha256 == null) {
        error("The SHA-256 sum for $assetName is missing")
      }
      return downloader.downloadWithSha256(tag, assetName, expectedSha256)
    }
  }

  /** Does not provide checksums. */
  data object None : GitHubReleaseChecksumResolver() {
    override suspend fun download(downloader: GitHubReleaseAssetDownloader, tag: String, assetName: String): Path =
      downloader.downloadCached(tag, assetName)
  }
}
