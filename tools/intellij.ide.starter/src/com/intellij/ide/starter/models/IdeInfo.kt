package com.intellij.ide.starter.models

import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.teamcity.TeamCityCIServer

data class IdeInfo(
  val productCode: String,
  val platformPrefix: String,
  val executableFileName: String,
  val buildType: String,
  val buildNumber: String? = null,
  val tag: String? = null
) {
  companion object {
    fun new(
      productCode: String,
      platformPrefix: String,
      executableFileName: String,
      jetBrainsCIBuildType: String,
      buildNumber: String? = null,
      tag: String? = null
    ): IdeInfo {
      // TODO: deal later with custom build number on CI (shouldn't affect anyone outside JB for now)
      if (TeamCityCIServer.isBuildRunningOnCI && tag == null) {
        val paramBuildType = TeamCityCIServer.buildParams["intellij.installer.build.type"]
        val paramBuildNumber = TeamCityCIServer.buildParams["intellij.installer.build.number"]
        if (paramBuildType != null && paramBuildNumber != null) {
          return IdeInfo(productCode, platformPrefix, executableFileName, paramBuildType, paramBuildNumber, null)
        }
      }

      return IdeInfo(productCode, platformPrefix, executableFileName, jetBrainsCIBuildType, buildNumber, tag)
    }
  }

  internal val installerFilePrefix
    get() = when (productCode) {
      "IU" -> "ideaIU"
      "IC" -> "ideaIC"
      "WS" -> "WebStorm"
      "PS" -> "PhpStorm"
      "DB" -> "datagrip"
      "GO" -> "goland"
      "RM" -> "RubyMine"
      "PY" -> "pycharmPY"
      else -> error("Unknown product code: $productCode")
    }

  internal val installerFileExt
    get() = when {
      SystemInfo.isWindows -> ".win.zip"
      SystemInfo.isLinux -> ".tar.gz"
      SystemInfo.isMac -> when (System.getProperty("os.arch")) {
        "x86_64" -> ".dmg"
        "aarch64" -> "-aarch64.dmg"
        else -> error("Unknown architecture of Mac OS")
      }
      else -> error("Unknown OS")
    }
}
