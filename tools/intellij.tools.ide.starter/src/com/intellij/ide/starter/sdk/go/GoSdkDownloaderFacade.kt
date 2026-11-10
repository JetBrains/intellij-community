package com.intellij.ide.starter.sdk.go

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useDockerContainer
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
import com.intellij.util.text.SemVer
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
   * Fetches the latest available Go SDK version, including a beta or an RC, from the Go module proxy through Cache Redirector.
   *
   * The proxy lists each release of the `golang.org/toolchain` module once per platform and in no particular order.
   */
  fun getLatestAvailableVersion(): String {
    val url = "https://cache-redirector.jetbrains.com/proxy.golang.org/golang.org/toolchain/@v/list"
    logOutput("Fetching Go versions from $url")
    val response = java.net.URI(url).toURL().openStream().bufferedReader().use { it.readText() }
    val latest = response.lineSequence()
                   .mapNotNull { toolchainLinuxAmd64.matchEntire(it)?.groups["version"]?.value }
                   .mapNotNull { version ->
                     val semVer = version.replace(minorRelease, "$0.0").replace(preRelease, "-$0.")
                     SemVer.parseFromText(semVer)?.let { version to it }
                   }
                   .maxByOrNull { (_, semVer) -> semVer }
                   ?.first
                 ?: error("No Go versions found at $url")
    logOutput("Latest available Go version: $latest")
    return latest
  }

  private val toolchainLinuxAmd64 = Regex("""v0\.0\.1-go(?<version>.+)\.linux-amd64""")

  /**
   * A Go release without a patch number, such as `1.21` in `1.21` and `1.27rc3`; [SemVer] needs `1.21.0` and `1.27.0-rc.3`.
   */
  private val minorRelease = Regex("""^\d+\.\d+(?=beta|rc|$)""")

  /**
   * The pre-release of a Go release, such as `rc` in `1.27rc3`; [SemVer] needs `-rc.3`, which sorts a beta before an RC
   * and an RC before the release.
   */
  private val preRelease = Regex("beta|rc")

  /**
   * Returns platform info for the platform where the IDE runs.
   * A remote target gives its own platform.
   * A local target, or a remote target whose platform cannot be resolved, gives the host platform.
   * A Docker run reports a local target, but its IDE runs in a Linux container, so it gets Linux on the host architecture.
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
      val os = when {
        ConfigurationStorage.useDockerContainer() -> "linux"
        OS.CURRENT == OS.Windows -> "windows"
        OS.CURRENT == OS.Linux -> "linux"
        OS.CURRENT == OS.macOS -> "darwin"
        else -> throw DownloadGoSdkException("Unknown OS")
      }
      PlatformInfo(
        os = os,
        arch = if (CpuArch.isArm64()) "arm64" else "amd64",
        extension = if (os == "windows") ".zip" else ".tar.gz"
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
}
