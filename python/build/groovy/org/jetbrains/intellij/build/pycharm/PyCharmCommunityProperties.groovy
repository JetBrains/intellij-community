/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.intellij.build.*

/**
 * @author nik
 */
class PyCharmCommunityProperties extends PyCharmPropertiesBase {
  PyCharmCommunityProperties(String communityHome) {
    productCode = "PC"
    platformPrefix = "PyCharmCore"
    applicationInfoModule = "python-community-ide-resources"
    brandingResourcePaths = ["$communityHome/python/resources"]

    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + ["dom-openapi"]
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + [
      "dom-impl", "python-community", "python-community-ide-resources",
      "python-community-ide", "python-community-configure", "python-openapi", "python-psi-api", "platform-main"
    ]
    productLayout.bundledPluginModules = new File("$communityHome/python/build/plugin-list.txt").readLines()
    productLayout.mainModules = ["main_pycharm_ce"]
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    super.copyAdditionalFiles(context, targetDirectory)
    context.ant.copy(todir: "$targetDirectory/license") {
      fileset(file: "$context.paths.communityHome/LICENSE.txt")
      fileset(file: "$context.paths.communityHome/NOTICE.txt")
    }
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) {
    "PyCharmCE${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharmPC-$buildNumber"
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new PyCharmWindowsDistributionCustomizer() {
      {
        installerImagesPath = "$projectHome/python/build/resources"
        fileAssociations = [".py"]
      }

      @Override
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) {
        "PyCharm Community Edition"
      }

      @Override
      String getBaseDownloadUrlForJre() { "https://download.jetbrains.com/python" }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$projectHome/python/resources/PyCharmCore128.png"
        //snapName = "pycharm-community"
        //snapDescription =
        //  "Python IDE for professional developers. Save time while PyCharm takes care of the routine. "
        //  "Focus on bigger things and embrace the keyboard-centric approach to get the most of PyCharm’s many productivity features."
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        "pycharm-community-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
      }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmMacDistributionCustomizer() {
      {
        icnsPath = "$projectHome/python/resources/PyCharmCore.icns"
        bundleIdentifier = "com.jetbrains.pycharm"
        dmgImagePath = "$projectHome/python/build/DMG_background.png"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        String suffix = applicationInfo.isEAP ? " ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} EAP" : ""
        "PyCharm CE${suffix}.app"
      }
    }
  }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) {
    "pycharm-ce"
  }
}