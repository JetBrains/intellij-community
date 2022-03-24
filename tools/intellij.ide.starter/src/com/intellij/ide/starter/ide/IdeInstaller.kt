package com.intellij.ide.starter.ide

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.teamcity.TeamCityClient
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.asSequence

data class IdeInstaller(
  val installerFile: Path,
  val ideInfo: IdeInfo,
  val buildNumber: String
) {
  companion object {

    fun resolveIdeInstaller(ideInfo: IdeInfo, useLastAvailableIdeBuild: Boolean, ciServer: CIServer): IdeInstaller {
      val installersDirectory = (di.direct.instance<GlobalPaths>().installersDirectory / ideInfo.productCode).createDirectories()

      if (ciServer.isBuildRunningOnCI && ideInfo.productCode != "IC" && ideInfo.tag.isNullOrEmpty()) {
        val key = "ide-installer-file"
        if (ciServer.buildParams.containsKey(key)) {
          val installerFile = Path(ciServer.buildParams.getValue(key))
          require(installerFile.isRegularFile()) { "TeamCity provided $key must point to an IDE installer file: $installerFile" }
          return IdeInstaller(installerFile, ideInfo, "installer-from-file")
        }
      }

      if (ideInfo.buildNumber != null && ideInfo.productCode != "IC") {
        val (ciBuildId, ciBuildNumber) = TeamCityClient.getLastSuccessfulBuild(ideInfo)
        return downloadIdeInstallerIfNecessary(ideInfo, ciBuildId, ciBuildNumber, installersDirectory)
      }

      val localInstallers = Files.list(installersDirectory).use { files ->
        files.asSequence().filter {
          val fileName = it.fileName.toString()
          fileName.startsWith(ideInfo.installerFilePrefix) && fileName.endsWith(ideInfo.installerFileExt)
        }.toList()
      }

      val snapshotInstaller = localInstallers.find { it.fileName.toString().contains("SNAPSHOT", true) }
      if (snapshotInstaller != null) {
        logOutput("Found SNAPSHOT IDE installer locally: $snapshotInstaller")
        return IdeInstaller(snapshotInstaller, ideInfo, "SNAPSHOT")
      }

      logOutput("Locating the latest success build for $ideInfo")
      val (ciBuildId, ciBuildNumber) = TeamCityClient.getLastSuccessfulBuild(ideInfo)

      if (useLastAvailableIdeBuild) {
        val localIde = localInstallers.mapNotNull { ide ->
          val fileName = ide.fileName.toString()
          val buildNumberStr = fileName.substringAfter(ideInfo.installerFilePrefix + "-").substringBefore(ideInfo.installerFileExt)
          val buildNumber = BuildNumber.fromStringOrNull(buildNumberStr)
          if (buildNumber != null) buildNumber to ide else null
        }.maxByOrNull { it.first }

        if (localIde != null) {
          val (buildNumber, ide) = localIde
          if (buildNumber.asStringWithoutProductCode() != ciBuildNumber) {
            logOutput(
              "Using the latest locally available IDE build $buildNumber that is different from the latest available from Installers on CI ($ciBuildNumber")
            return IdeInstaller(ide, ideInfo, buildNumber.asStringWithoutProductCode())
          }
        }
      }

      return downloadIdeInstallerIfNecessary(ideInfo, ciBuildId, ciBuildNumber, installersDirectory)
    }

    private fun downloadIdeInstallerIfNecessary(
      ideInfo: IdeInfo,
      ciBuildId: String,
      ciBuildNumber: String,
      installersDirectory: Path
    ): IdeInstaller {
      val installerName = "${ideInfo.installerFilePrefix}-$ciBuildNumber${ideInfo.installerFileExt}"
      val installerFile = installersDirectory.resolve(installerName)

      if (installerFile.exists()) {
        logOutput("Found locally available IDE installer for $ideInfo at $installerFile")
      }
      else {
        TeamCityClient.downloadArtifact(ciBuildId, installerName, installerFile.toFile())
      }

      return IdeInstaller(installerFile, ideInfo, ciBuildNumber)
    }
  }
}