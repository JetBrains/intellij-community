// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MacDistributionCustomizer
import java.nio.file.Path

open class PyCharmMacDistributionCustomizer : MacDistributionCustomizer() {
  override fun copyAdditionalFiles(context: BuildContext, targetDirectory: Path) {
    super.copyAdditionalFiles(context, targetDirectory)
    PyCharmBuildUtils.copySkeletons(context, targetDirectory, "skeletons-mac*.zip")
  }

  override fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> {
    return mapOf("ide.mac.useNativeClipboard" to "false")
  }
}
