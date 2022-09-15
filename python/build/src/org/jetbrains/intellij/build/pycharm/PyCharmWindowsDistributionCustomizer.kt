// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

open class PyCharmWindowsDistributionCustomizer : WindowsDistributionCustomizer() {
  override fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: String) {
    PyCharmBuildUtils.copySkeletons(context, targetDirectory, "skeletons-win*.zip")
  }

  override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? {
    return "https://www.jetbrains.com/pycharm/uninstall/?version=${appInfo.productCode}-${appInfo.majorVersion}.${appInfo.minorVersion}"
  }
}