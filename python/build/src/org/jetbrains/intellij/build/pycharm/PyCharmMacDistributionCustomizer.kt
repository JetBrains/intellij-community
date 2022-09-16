// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MacDistributionCustomizer

open class PyCharmMacDistributionCustomizer(extraExecutables: List<String> = emptyList()) : MacDistributionCustomizer(extraExecutables = extraExecutables) {
  override fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
    super.copyAdditionalFiles(context, targetDirectory)
    PyCharmBuildUtils.copySkeletons(context, targetDirectory, "skeletons-mac*.zip")
  }

  override fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> {
    return mapOf("ide.mac.useNativeClipboard" to "false")
  }
}