// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

class PyCharmWindowsDistributionCustomizer extends WindowsDistributionCustomizer {
  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def underTeamCity = System.getProperty("teamcity.buildType.id") != null

    context.ant.copy(todir: "$targetDirectory/skeletons", failonerror: underTeamCity) {
      fileset(dir: "$context.paths.projectHome/skeletons", erroronmissingdir: underTeamCity) {
        include(name: "skeletons-win*.zip")
      }
    }
  }

  @Override
  String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    "https://www.jetbrains.com/pycharm/uninstall/"
  }
}