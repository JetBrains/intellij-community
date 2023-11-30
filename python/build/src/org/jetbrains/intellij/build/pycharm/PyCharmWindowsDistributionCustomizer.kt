// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import java.nio.file.Path

open class PyCharmWindowsDistributionCustomizer : WindowsDistributionCustomizer() {
  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
    super.copyAdditionalFiles(context, targetDir, arch)
    PyCharmBuildUtils.copySkeletons(context, targetDir, "skeletons-win*.zip")
  }

  override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? {
    return "https://www.jetbrains.com/pycharm/uninstall/?version=${appInfo.productCode}-${appInfo.majorVersion}.${appInfo.minorVersion}"
  }
}
