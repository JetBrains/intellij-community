package com.intellij.ide.starter.community

import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.system.OsType
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import java.nio.file.Path
import kotlin.io.path.exists

object PublicIdeDownloader : IdeDownloader {

  override fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path): IdeInstaller {
    val releaseInfoMap = JetBrainsDataServiceClient.getReleases(
      ProductInfoRequestParameters(code = ideInfo.productCode,
                                   type = ideInfo.buildType,
                                   build = ideInfo.buildNumber)
    )

    if (releaseInfoMap.size == 1) {
      //Find the latest build
      val possibleBuild = releaseInfoMap.values.first().sortedByDescending { it.date }.first()

      val downloadLink: String = when (SystemInfo.getOsType()) {
        OsType.Linux -> possibleBuild.downloads.linux!!.link
        OsType.MacOS -> possibleBuild.downloads.mac!!.link
        OsType.Windows -> possibleBuild.downloads.windows!!.link
        else -> throw RuntimeException("Unsupported OS ${SystemInfo.getOsType()}")
      }

      val installerFile = installerDirectory.resolve(
        "${ideInfo.installerFilePrefix}-" + possibleBuild.build.replace(".", "") + ideInfo.installerFileExt
      )

      if (!installerFile.exists()) {
        logOutput("Downloading $ideInfo ...")
        HttpClient.download(downloadLink, installerFile)
      }
      else logOutput("Installer file $installerFile already exists. Skipping download.")

      return IdeInstaller(installerFile, possibleBuild.build)
    }
    else {
      throw RuntimeException("Only one product can be handled. Found ${releaseInfoMap.keys}")
    }
  }
}