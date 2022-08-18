// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MacDistributionCustomizer

@CompileStatic
class PyCharmMacDistributionCustomizer extends MacDistributionCustomizer {
  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    super.copyAdditionalFiles(context, targetDirectory)
    PyCharmBuildUtils.copySkeletons(context, targetDirectory, "skeletons-mac*.zip")
  }

  @Override
  Map<String, String> getCustomIdeaProperties(ApplicationInfoProperties applicationInfo) {
    return Collections.singletonMap("ide.mac.useNativeClipboard", "false")
  }
}