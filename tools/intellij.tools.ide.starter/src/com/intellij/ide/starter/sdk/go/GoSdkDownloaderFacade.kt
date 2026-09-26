package com.intellij.ide.starter.sdk.go

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.runner.targets.TargetIdentifier
import com.intellij.ide.starter.runner.targets.isLocal
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.isArm64
import com.intellij.platform.eel.isLinux
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.isWindows
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetryBlocking
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

class DownloadGoSdkException(message: String) : SetupException(message)

object GoSdkDownloaderFacade {

  /**
   * Downloads the Go SDK for the specified version and current target platform.
   * Uses lazy initialization - the actual download happens on first access to [GoSdkDownloadItem.home].
   */
  fun goSdk(version: String): GoSdkDownloadItem {
    val platformInfo = getTargetPlatformInfo()
    return GoSdkDownloadItem(version, platformInfo.os, platformInfo.arch) {
      downloadGoSdkItem(version, platformInfo)
    }
  }

  /**
   * Fetches the latest available Go SDK version (including RC) from the go.dev/dl API.
   */
  fun getLatestAvailableVersion(): String {
    val url = "https://go.dev/dl/?mode=json&include=all"
    logOutput("Fetching Go versions from $url")
    val response = java.net.URI(url).toURL().openStream().bufferedReader().use { it.readText() }
    val mapper = jacksonObjectMapper()
    val versions = mapper.readValue(response, Array<GoVersionInfo>::class.java)
    val latest = versions?.firstOrNull()
                 ?: error("No Go versions found at $url")
    logOutput("Latest available Go version: ${latest.version}")
    return latest.version.removePrefix("go")
  }

  /**
   * Returns platform info for the current target (local or remote).
   * Falls back to SystemInfo when the target is local or cannot be resolved.
   */
  fun getTargetPlatformInfo(): PlatformInfo {
    val platform = getTargetPlatform()
    return if (platform != null) {
      PlatformInfo(
        os = when {
          platform.isWindows -> "windows"
          platform.isLinux -> "linux"
          platform.isMac -> "darwin"
          else -> throw DownloadGoSdkException("Unknown OS: $platform")
        },
        arch = if (platform.isArm64) "arm64" else "amd64",
        extension = if (platform.isWindows) ".zip" else ".tar.gz"
      )
    }
    else {
      PlatformInfo(
        os = when (OS.CURRENT) {
            OS.Windows -> "windows"
            OS.Linux -> "linux"
            OS.macOS -> "darwin"
            else -> throw DownloadGoSdkException("Unknown OS")
        },
        arch = if (CpuArch.isArm64()) "arm64" else "amd64",
        extension = if (OS.CURRENT == OS.Windows) ".zip" else ".tar.gz"
      )
    }
  }

  private fun downloadGoSdkItem(version: String, platformInfo: PlatformInfo): GoSdkPaths {
    val cacheKey = "$version-${platformInfo.os}-${platformInfo.arch}"
    val installPath = GlobalPaths.instance.getCacheDirectoryFor("go-sdk/$cacheKey")
    val goHome = installPath.resolve("go")

    logOutput("Checking Go SDK at $installPath")

    if (!goHome.exists()) {
      downloadAndInstallGoSdk(version, platformInfo, installPath)
    }

    // The caller writes this path into the `GOROOT` of the IDE. An absent directory is not a usable SDK:
    // the IDE drops it and takes the `GOROOT` of the host machine instead. Then the test runs against the wrong Go.
    if (!goHome.exists()) {
      throw DownloadGoSdkException("The Go SDK $version is not at $goHome after the download")
    }

    return GoSdkPaths(homePath = goHome, installPath = installPath)
  }

  @OptIn(ExperimentalPathApi::class)
  private fun downloadAndInstallGoSdk(version: String, platformInfo: PlatformInfo, installPath: Path) {
    val sdkFileName = "go$version.${platformInfo.os}-${platformInfo.arch}${platformInfo.extension}"
    val url = "https://cache-redirector.jetbrains.com/dl.google.com/go/$sdkFileName"
    val downloadedFile = installPath.resolve(sdkFileName)

    withRetryBlocking(messageOnFailure = "Failure on downloading/installing Go SDK", retries = 3) {
      logOutput("Downloading Go SDK from $url to $installPath")

      installPath.deleteRecursively()
      Files.createDirectories(installPath)

      HttpClient.download(url, downloadedFile)
      FileSystem.unpack(downloadedFile, installPath)

      downloadedFile.deleteIfExists()
    }
    // `withRetryBlocking` returns null after the last try, and it logs the error only.
    ?: throw DownloadGoSdkException("Failed to download and install the Go SDK $version from $url. See the log above.")
  }

  private fun getTargetPlatform(): EelPlatform? {
    return try {
      if (TargetIdentifier.current.isLocal()) null
      else TargetIdentifier.current.eelApi.platform
    }
    catch (_: Exception) {
      null
    }
  }

  data class PlatformInfo(val os: String, val arch: String, val extension: String)

  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class GoVersionInfo(val version: String = "", val stable: Boolean = false)
}
