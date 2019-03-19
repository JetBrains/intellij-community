// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.LinuxDistributionCustomizer

class PyCharmLinuxDistributionCustomizer  extends LinuxDistributionCustomizer {
  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    context.ant.mkdir(dir: "$targetDirectory/$PyCharmBuildOptions.minicondaInstallerFolderName")
    context.ant.get(src: "https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh",
                    dest: "$targetDirectory/$PyCharmBuildOptions.minicondaInstallerFolderName")
  }
}
