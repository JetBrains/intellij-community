// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

class PyCharmWindowsDistributionCustomizer extends WindowsDistributionCustomizer {
  @Override
  void copyAdditionalFilesBlocking(BuildContext context, String targetDirectory) {
    super.copyAdditionalFilesBlocking(context, targetDirectory)
    PyCharmBuildUtils.copySkeletons(context, targetDirectory, "skeletons-win*.zip")
  }

  @Override
  String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    "https://www.jetbrains.com/pycharm/uninstall/?version=${applicationInfo.productCode}-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
  }
}