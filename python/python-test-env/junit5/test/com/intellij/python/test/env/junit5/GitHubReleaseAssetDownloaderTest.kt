// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.idea.TestFor
import com.intellij.python.test.env.core.GitHubReleaseAssetDownloader
import com.intellij.python.test.env.core.GitHubReleaseChecksumResolver
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Subsystems.Interpreters
@Layers.Functional
@TestFor(issues = ["PY-91711"])
internal class GitHubReleaseAssetDownloaderTest {
  @Test
  fun `one downloader supports different release tags`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val asset = Files.writeString(tempDir.resolve("tool.zip"), "tool")
    val requestedUrls = mutableListOf<String>()
    val downloader = GitHubReleaseAssetDownloader(
      repository = "owner/tool",
      cacheDownload = { url, _ ->
        requestedUrls.add(url)
        asset
      },
    )

    downloader.download("v1.0.0", "tool.zip")
    downloader.download("v2.0.0", "tool.zip")

    assertThat(requestedUrls).containsExactly(
      "https://cache-redirector.jetbrains.com/github.com/owner/tool/releases/download/v1.0.0/tool.zip",
      "https://cache-redirector.jetbrains.com/github.com/owner/tool/releases/download/v2.0.0/tool.zip",
    )
  }

  @Test
  fun `GitHub API digest configures cache validation`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val invalidAsset = Files.writeString(tempDir.resolve("invalid-tool"), "invalid")
    val validAsset = Files.writeString(tempDir.resolve("valid-tool"), "downloaded tool")
    val releaseMetadata = Files.writeString(
      tempDir.resolve("github-release.json"),
      """{"assets":[{"name":"tool","digest":"sha256:0d3b228f8eb7482929de5a68d90f8eff0de8600f46c4918682e61a4be20d6154"}]}""",
    )
    var assetDownloads = 0
    val requestedUrls = mutableListOf<String>()
    val verifiedDownloader = GitHubReleaseAssetDownloader(
      repository = "owner/tool",
      checksumResolver = GitHubReleaseChecksumResolver.GitHubApiDigest,
      cacheDownload = { url, assetName ->
        requestedUrls.add(url)
        when (assetName) {
          "github-release.json" -> releaseMetadata
          else -> error("Unexpected asset: $assetName")
        }
      },
      validatedCacheDownload = { url, assetName, isValid ->
        requestedUrls.add(url)
        if (assetName != "tool") error("Unexpected asset: $assetName")
        val first = if (assetDownloads++ == 0) invalidAsset else validAsset
        if (isValid(first)) first else validAsset.also { assetDownloads++ }
      },
    )

    assertThat(verifiedDownloader.download("v1.0.0", "tool")).isEqualTo(validAsset)
    assertThat(assetDownloads).isEqualTo(2)
    assertThat(requestedUrls).containsExactly(
      "https://api.github.com/repos/owner/tool/releases/tags/v1.0.0",
      "https://cache-redirector.jetbrains.com/github.com/owner/tool/releases/download/v1.0.0/tool",
    )
  }

  @Test
  fun `second invalid asset fails verification`(@TempDir tempDir: Path) {
    val invalidAssets = listOf(
      Files.writeString(tempDir.resolve("invalid-tool-1"), "invalid"),
      Files.writeString(tempDir.resolve("invalid-tool-2"), "still invalid"),
    )
    val manifest = Files.writeString(tempDir.resolve("sha256.sum"), "${"0".repeat(64)} *tool")
    var assetDownloads = 0
    val verifiedDownloader = GitHubReleaseAssetDownloader(
      repository = "owner/tool",
      checksumResolver = GitHubReleaseChecksumResolver.GnuManifest("sha256.sum"),
      cacheDownload = { _, assetName ->
        when (assetName) {
          "sha256.sum" -> manifest
          else -> error("Unexpected asset: $assetName")
        }
      },
      validatedCacheDownload = { _, assetName, isValid ->
        if (assetName != "tool") error("Unexpected asset: $assetName")
        val first = invalidAssets[assetDownloads++]
        if (isValid(first)) first
        else {
          val second = invalidAssets[assetDownloads++]
          if (isValid(second)) second else error("The downloaded file did not pass validation")
        }
      },
    )

    assertThatThrownBy { timeoutRunBlocking { verifiedDownloader.download("v1.0.0", "tool") } }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("did not pass validation")
    assertThat(assetDownloads).isEqualTo(2)
  }
}
