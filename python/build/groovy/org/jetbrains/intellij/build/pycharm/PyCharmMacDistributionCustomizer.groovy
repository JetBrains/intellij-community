/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MacDistributionCustomizer

/**
 * @author nik
 */
class PyCharmMacDistributionCustomizer extends MacDistributionCustomizer {
  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def underTeamCity = System.getProperty("teamcity.buildType.id") != null

    context.ant.copy(todir: "$targetDirectory/skeletons", failonerror: underTeamCity) {
      fileset(dir: "$context.paths.projectHome/skeletons", erroronmissingdir: underTeamCity) {
        include(name: "skeletons-mac*.zip")
      }
    }

    context.ant.mkdir(dir: "$targetDirectory/$PyCharmBuildOptions.minicondaInstallerFolderName")
    context.ant.get(src: "https://repo.continuum.io/miniconda/Miniconda3-latest-MacOSX-x86_64.sh",
                    dest: "$targetDirectory/$PyCharmBuildOptions.minicondaInstallerFolderName")
  }

  @Override
  Map<String, String> getCustomIdeaProperties(ApplicationInfoProperties applicationInfo) {
    ["ide.mac.useNativeClipboard": "false"]
  }
}