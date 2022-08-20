package com.intellij.ide.starter.community

import com.intellij.ide.starter.ide.IDEResolver
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.system.SystemInfo
import java.nio.file.Path
import kotlin.io.path.exists

object PublicIdeResolver : IDEResolver {

  override fun resolveIDE(ideInfo: IdeInfo, installerPath: Path): IdeInstaller {
    val releaseInfoMap = DataServiceClient.getReleases(ProductInfoRequestParameters(
      code = ideInfo.productCode,
      type = ideInfo.buildType,
      build = ideInfo.buildNumber)
    )

    if (releaseInfoMap.size == 1) {
      //Find the latest build
      val possibleBuild = releaseInfoMap.values.iterator().next().sortedByDescending { it.date }[0]
      var downloadLink = ""
      if (SystemInfo.isLinux) downloadLink = possibleBuild.downloads.linux!!.link
      else if (SystemInfo.isMac) downloadLink = possibleBuild.downloads.mac!!.link
      else if (SystemInfo.isWindows) downloadLink = possibleBuild.downloads.windows!!.link
      else throw RuntimeException("Unsupported OS ${SystemInfo.getOsType()}")

      val installerFile = installerPath.resolve("${ideInfo.installerFilePrefix}-" + possibleBuild.build.replace(".", "") + ideInfo.installerFileExt)
      if (!installerFile.exists()) DataServiceClient.downloadIDE(downloadLink, installerFile)
      return IdeInstaller(installerFile, possibleBuild.build)
    }
    else {
      throw RuntimeException("Only one product can be handled. Found ${releaseInfoMap.keys}")
    }
  }

  data class ProductInfoRequestParameters(val code: String,
                                          val type: String? = null,
                                          val majorVersion: String? = null,
                                          val build: String? = null) {

    override fun toString(): String {
      val paramString = "?code=$code"
      if (type != null) "$paramString?type=$type"
      if (majorVersion != null) "$paramString?majorVersion=$majorVersion"
      if (build != null) "$paramString?build=$build"
      return paramString
    }
  }

}