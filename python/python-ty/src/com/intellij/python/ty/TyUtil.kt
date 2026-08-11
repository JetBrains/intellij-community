package com.intellij.python.ty

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.python.ty.icons.PythonTYIcons
import com.intellij.util.IconUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import javax.swing.Icon
import kotlin.io.path.div
import kotlin.io.path.setPosixFilePermissions

private const val TY_GITHUB_API_URL = "https://api.github.com/repos/JetBrains/ty/releases/latest"

object TyUtil {
  private val LOG = logger<TyUtil>()

  fun getDefaultTyIcon(): Icon {
    return IconUtil.downscaleIconToSize(PythonTYIcons.TY, 16, 16)
  }

  /**
   * Gets the installation directory for ty binary in the IDE's bin directory.
   */
  fun getTyInstallationDirectory(): Path {
    return PathManager.getBinDir()
  }

  /**
   * Gets the expected binary name for the current platform.
   */
  fun getTyBinaryName(): String {
    return if (OS.CURRENT == OS.Windows) "ty.exe" else "ty"
  }

  /**
   * Checks if ty is already installed in the IDE's bin directory.
   */
  fun isTyInstalled(): Boolean {
    val installDir = getTyInstallationDirectory()
    val binaryName = getTyBinaryName()
    val binaryPath = installDir / binaryName
    return Files.exists(binaryPath)
  }

  /**
   * Fetches the latest release information from GitHub API.
   */
  fun fetchLatestRelease(): TyReleaseInfo? {
    return try {
      fetchLatestReleaseImpl()
    }
    catch (e: Exception) {
      LOG.error("Failed to fetch latest Ty release from GitHub", e)
      null
    }
  }

  private fun fetchLatestReleaseImpl(): TyReleaseInfo? {
    val response = HttpRequests.request(TY_GITHUB_API_URL)
      .userAgent("intellij-ty-plugin")
      .readString()

    val mapper = ObjectMapper()
    val jsonNode = mapper.readTree(response)

    val tagName = jsonNode.get("tag_name")?.asText() ?: return null
    val assets = jsonNode.get("assets") ?: return null

    val platformSuffix = getPlatformSuffix()
    val asset = assets.find { node ->
      val name = node.get("name")?.asText() ?: ""
      name.contains(platformSuffix, ignoreCase = true)
    } ?: return null

    val downloadUrl = asset.get("browser_download_url")?.asText() ?: return null

    return TyReleaseInfo(tagName, downloadUrl)
  }

  /**
   * Downloads the ty binary for the current platform to the IDE's bin directory.
   */
  fun downloadTyBinary(indicator: ProgressIndicator? = null): Path? {
    return try {
      downloadTyBinaryImpl(indicator)
    }
    catch (e: Exception) {
      LOG.error("Failed to download ty binary", e)
      null
    }
  }

  fun downloadTyBinaryImpl(indicator: ProgressIndicator? = null): Path? {
    indicator?.text = TyBundle.message("install.ty.progress.fetching")
    val releaseInfo = fetchLatestRelease() ?: return null

    LOG.info("Downloading ty ${releaseInfo.version} from ${releaseInfo.downloadUrl}")

    indicator?.text = TyBundle.message("install.ty.progress.downloading", releaseInfo.version)
    val installDir = getTyInstallationDirectory()
    Files.createDirectories(installDir)

    val binaryPath = installDir / getTyBinaryName()

    HttpRequests.request(releaseInfo.downloadUrl)
      .userAgent("intellij-ty-Plugin")
      .saveToFile(binaryPath, indicator)

    // Make executable on Unix-like systems
    if (OS.CURRENT != OS.Windows) {
      try {
        binaryPath.setPosixFilePermissions(
          setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE
          )
        )
      }
      catch (e: IOException) {
        LOG.error("Failed to set executable permissions on ty binary", e)
        return null
      }
    }

    LOG.info("Successfully downloaded ty to $binaryPath")
    return binaryPath
  }

  private fun getPlatformSuffix(): String {
    val os = when {
      SystemInfo.isWindows -> "pc-windows-msvc"
      SystemInfo.isLinux -> "unknown-linux-TODO" // TODO: linux type: musl, gnu,
      SystemInfo.isMac -> "apple-darwin"
      else -> return ""
    }

    val arch = when (CpuArch.CURRENT) {
      CpuArch.X86_64 -> "x86_64"
      CpuArch.ARM64 -> "aarch64"
      else -> return ""
    }

    return "$arch-$os"
  }
}

data class TyReleaseInfo(
  val version: String,
  val downloadUrl: String,
)
