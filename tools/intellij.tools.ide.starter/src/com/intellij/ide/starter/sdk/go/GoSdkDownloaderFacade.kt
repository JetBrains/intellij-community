package com.intellij.ide.starter.sdk.go

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.runner.targets.TargetIdentifier
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.*
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
   * Returns platform info for the current target (local or remote).
   * Falls back to SystemInfo when Application is not initialized.
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
  }

  private fun getTargetPlatform(): EelPlatform? {
    if (ApplicationManager.getApplication() == null) {
      return null
    }
    return try {
      TargetIdentifier.current.eelApi.platform
    }
    catch (_: Exception) {
      null
    }
  }

  data class PlatformInfo(val os: String, val arch: String, val extension: String)
}
