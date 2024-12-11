// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.MacDistributionCustomizer
import java.nio.file.Path

open class PyCharmMacDistributionCustomizer(projectHome: Path) : MacDistributionCustomizer() {
  init {
    icnsPath = "$projectHome/python/build/resources/PyCharmCore.icns"
    icnsPathForEAP = "$projectHome/python/build/resources/PyCharmCore_EAP.icns"
    bundleIdentifier = "com.jetbrains.pycharm.ce"
    dmgImagePath = "$projectHome/python/build/resources/dmg_background.tiff"
  }

  override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    val suffix = if (appInfo.isEAP) " ${appInfo.majorVersion}.${appInfo.minorVersion} EAP" else ""
    return "PyCharm CE${suffix}.app"
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
    super.copyAdditionalFiles(context, targetDir, arch)
    PyCharmBuildUtils.copySkeletons(context, targetDir, "skeletons-mac*.zip")
  }

  override fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> {
    return mapOf("ide.mac.useNativeClipboard" to "false")
  }
}
