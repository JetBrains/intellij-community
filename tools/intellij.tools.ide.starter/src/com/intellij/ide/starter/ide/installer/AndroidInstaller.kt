package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeArchiveExtractor
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.XmlBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.net.URI
import java.nio.file.Path
import javax.xml.xpath.XPathFactory
import kotlin.io.path.div

data class AndroidStudioReleaseInfo(val version: String, val majorVersion: String, val build: String)

class AndroidInstaller : IdeInstaller {
  companion object {
    /**
     * Get latest release Android Studio version from https://dl.google.com/android/studio/patches/updates.xml
     * @return version string, e.g. 2023.1.1.1
     */
    fun fetchLatestMajorReleaseVersion(): AndroidStudioReleaseInfo {
      val uri = URI.create("https://dl.google.com/android/studio/patches/updates.xml")
      uri.toURL().openStream().use { input ->
        val doc = XmlBuilder.parse(input)
        val xPath = XPathFactory.newInstance().newXPath()

        val rawVersion = runCatching {
          xPath.evaluate("/products/product/channel[@status='release']/build[1]/@version", doc)
            .takeIf { it.isNotBlank() }
        }.getOrNull()

        val majorVersion: String = rawVersion?.let { Regex("""\d+\.\d+\.\d+""").find(it)?.value }
                                     ?.takeIf { it.isNotEmpty() } ?: error("Failed to get Android Studio build number")
        val patchNumber: Int? = Regex("Patch (\\d+)", RegexOption.IGNORE_CASE)
          .find(rawVersion)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val version = if (patchNumber == null) majorVersion else "$majorVersion.$patchNumber"

        val buildNumber = requireNotNull(
          xPath.evaluate("/products/product/channel[@status='release']/build[1]/@number", doc)
            .takeIf { it.isNotBlank() }
        ) { "Build number is not specified for Android Studio $version" }

        return AndroidStudioReleaseInfo(
          version = version,
          majorVersion = majorVersion,
          build = buildNumber,
        )
      }
    }

    fun createDownloadableUrl(buildNumber: String, os: OS): String {
      val ext = when (os) {
        OS.Windows -> "-windows.zip"
        OS.macOS -> if (CpuArch.isArm64()) "-mac_arm.dmg" else "-mac.dmg"
        OS.Linux -> "-linux.tar.gz"
        else -> error("Not supported OS")
      }

      val path = when (os) {
        OS.macOS -> "install"
        else -> "ide-zips"
      }

      return "https://redirector.gvt1.com/edgedl/android/studio/$path/$buildNumber/android-studio-$buildNumber$ext"
    }
  }

  /**
   * Resolve platform specific android studio installer and return paths
   * @return Pair<InstallDir / InstallerFile>
   */
  fun downloadAndroidStudio(buildNumber: String): Pair<Path, File> {
    val downloadUrl = createDownloadableUrl(buildNumber, OS.CURRENT)
    val asFileName = downloadUrl.split("/").last()
    val globalPaths by di.instance<GlobalPaths>()
    val zipFile = globalPaths.getLocalCacheDirectoryFor("android-studio").resolve(asFileName)
    HttpClient.downloadIfMissing(downloadUrl, zipFile)

    val installDir = globalPaths.getLocalCacheDirectoryFor("builds") / "AI-$buildNumber"

    installDir.deleteRecursivelyQuietly()

    val installerFile = zipFile.toFile()

    return Pair(installDir, installerFile)
  }

  override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val installDir: Path
    val installerFile: File

    if (ideInfo.buildNumber.isBlank()) {
      throw IllegalArgumentException("Build is not specified, please, provide buildNumber as IdeProductProvider.AI.copy(buildNumber = \"2023.1.1.28\")")
    }
    downloadAndroidStudio(ideInfo.buildNumber).also {
      installDir = it.first
      installerFile = it.second
    }
    IdeArchiveExtractor.unpackIdeIfNeeded(installerFile, installDir.toFile())
    val installationPath = when (!SystemInfo.isMac) {
      true -> installDir.resolve("android-studio")
      false -> installDir
    }
    val ide = di.direct.instance<IdeDistributionFactory>().installIDE(installationPath.toFile(), ideInfo.executableFileName)
    return Pair(ide.build, ide)
  }
}