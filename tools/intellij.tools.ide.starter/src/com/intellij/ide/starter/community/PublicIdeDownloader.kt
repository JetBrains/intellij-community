package com.intellij.ide.starter.community

import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.installer.IdeInstallerFile
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.exists

open class PublicIdeDownloader : IdeDownloader {

  /** Filter release map: <ProductCode, List of releases> */
  private fun findSpecificRelease(
    releaseInfoMap: Map<String, List<ReleaseInfo>>,
    filteringParams: ProductInfoRequestParameters,
  ): ReleaseInfo {
    try {
      val sortedByDate = releaseInfoMap.values.first().sortedByDescending { it.date }

      val build = when {
        filteringParams.majorVersion.isNotBlank() -> sortedByDate.first { it.majorVersion == filteringParams.majorVersion }
        // find the latest release / eap, if no specific params were provided
        filteringParams.versionNumber.isBlank() && filteringParams.buildNumber.isBlank() -> sortedByDate.first()
        filteringParams.versionNumber.isNotBlank() -> {
          sortedByDate.first {
            // Rider (RD) might have versions suffixes after "-". E.g., 2024.3-EAP5. So we take the part before '-'.
            val version = it.version.substringBefore('-')
            version == filteringParams.versionNumber
          }
        }
        filteringParams.buildNumber.isNotBlank() -> sortedByDate.firstOrNull() { it.build == filteringParams.buildNumber }
                                                    ?: error("Build not found for $filteringParams")
        else -> null
      }

      if (build != null) {
        val expirationDate = build.date.plusDays(30)
        if (build.type == "eap" && expirationDate.isBefore(LocalDate.now())) {
          throw SetupException("EAP build ${build.build} expired on $expirationDate")
        }

        return build
      }
    }
    catch (e: Exception) {
      logError("Failed to find specific release by parameters $filteringParams")
      throw e
    }

    throw NoSuchElementException("Couldn't find specified release by parameters $filteringParams")
  }

  override fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path): IdeInstallerFile =
    downloadIdeInstaller(ideInfo, installerDirectory, false)

  fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path, downloadDedicatedInstaller: Boolean): IdeInstallerFile {
    val params = ProductInfoRequestParameters(type = ideInfo.productCode,
                                              snapshot = ideInfo.buildType,
                                              buildNumber = ideInfo.buildNumber,
                                              versionNumber = ideInfo.version)

    val releaseInfoMap = JetBrainsDataServiceClient.getReleases(params)
    if (releaseInfoMap.size != 1) throw RuntimeException("Only one product can be downloaded at once. Found ${releaseInfoMap.keys}")
    val possibleBuild: ReleaseInfo = findSpecificRelease(releaseInfoMap, params)

    val rawDownloadLink: String = when (OS.CURRENT) {
      OS.Linux -> {
        if (CpuArch.CURRENT == CpuArch.ARM64) {
          possibleBuild.downloads.linuxArm?.link ?: error("LinuxARM download link is not specified")
        }
        else {
          possibleBuild.downloads.linux?.link ?: error("Linux download link is not specified")
        }
      }
      OS.macOS -> {
        if (CpuArch.CURRENT == CpuArch.ARM64) {
          possibleBuild.downloads.macM1?.link ?: error("MacOS M1 download link is not specified")
        }
        else {
          possibleBuild.downloads.mac?.link ?: error("MacOS x64 download link is not specified")
        }
      }
      OS.Windows -> when {
        downloadDedicatedInstaller -> possibleBuild.downloads.windows?.link ?: error("Windows installer download link is not specified")
        CpuArch.CURRENT == CpuArch.ARM64 -> possibleBuild.downloads.windowsArm?.link ?: error("Windows ARM download link is not specified")
        else -> possibleBuild.downloads.windowsZip?.link ?: error("Windows download link is not specified")
      }
      else -> throw RuntimeException("Unsupported OS ${OS.CURRENT}")
    }

    val mappedDownloadLink = mapDownloadLink(rawDownloadLink)

    val installerFile = installerDirectory.resolve("${ideInfo.installerFilePrefix}-${possibleBuild.build}${ideInfo.installerFileExt}")

    if (!installerFile.exists()) {
      logOutput("Downloading $ideInfo from $mappedDownloadLink...")
      HttpClient.download(mappedDownloadLink, installerFile)
    }
    else logOutput("Installer file $installerFile already exists. Skipping download.")

    return IdeInstallerFile(installerFile, possibleBuild.build)
  }

  protected open fun mapDownloadLink(downloadLink: String): String = downloadLink
}
