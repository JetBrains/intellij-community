// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.*

import static org.jetbrains.intellij.build.pycharm.PyCharmPropertiesBase.downloadMiniconda

/**
 * @author Aleksey.Rostovskiy
 */
class PyCharmCommunityCondaProperties extends PyCharmCommunityProperties {
  PyCharmCommunityCondaProperties(String communityHome) {
    super(communityHome)
    customProductCode = "PCA"
    productLayout.bundledPluginModules.add("intellij.python.conda")
    productLayout.buildAllCompatiblePlugins = false
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    buildCrossPlatformDistribution = false
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharmPCA-$buildNumber"
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new PyCharmCommunityWindowsDistributionCustomizer(projectHome) {
      @Override
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) {
        "PyCharm Community Edition with Anaconda plugin"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        downloadMiniconda(context, targetDirectory, "Windows")
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new PyCharmCommunityLinuxDistributionCustomizer(projectHome) {
      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        "pycharm-community-anaconda-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
      }


      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        downloadMiniconda(context, targetDirectory, "Linux")
      }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmCommunityMacDistributionCustomizer(projectHome) {
      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        String suffix = applicationInfo.isEAP ? " ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} EAP" : ""
        "PyCharm CE with Anaconda plugin${suffix}.app"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        downloadMiniconda(context, targetDirectory, "MacOSX")
      }
    }
  }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) {
    "pycharm-ce-anaconda"
  }
}