// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.*

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

@CompileStatic
class PyCharmCommunityProperties extends PyCharmPropertiesBase {
  PyCharmCommunityProperties(String communityHome) {
    platformPrefix = "PyCharmCore"
    customProductCode = "PC"
    applicationInfoModule = "intellij.pycharm.community"
    brandingResourcePaths = ["$communityHome/python/resources".toString()]
    scrambleMainJar = false
    buildSourcesArchive = true

    productLayout.mainModules = ["intellij.pycharm.community.main"]
    productLayout.productApiModules = ["intellij.xml.dom"]
    productLayout.productImplementationModules = [
      "intellij.xml.dom.impl",
      "intellij.platform.main",
      "intellij.pycharm.community"
    ]
    productLayout.bundledPluginModules +=
      ["intellij.python.community.plugin",
       "intellij.pycharm.community.customization"
      ] + new File("$communityHome/python/build/plugin-list.txt").readLines()

    productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
      plugin("intellij.pycharm.community.customization") {
        directoryName = "pythonIDE"
        mainJarName = "python-ide.jar"
        withModule("intellij.pycharm.community.ide.impl", mainJarName)
        withModule("intellij.jupyter.viewOnly")
        withModule("intellij.jupyter.core")
      }
    ]
    productLayout.pluginModulesToPublish = ["intellij.python.community.plugin"]
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    super.copyAdditionalFiles(context, targetDirectory)
    context.ant.copy(todir: "$targetDirectory/license") {
      fileset(file: "$context.paths.communityHome/LICENSE.txt")
      fileset(file: "$context.paths.communityHome/NOTICE.txt")
    }
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "PyCharmCE${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharmPC-$buildNumber"
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new PyCharmCommunityWindowsDistributionCustomizer(projectHome)
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new PyCharmCommunityLinuxDistributionCustomizer(projectHome) {
      {
        snapName = "pycharm-community"
        snapDescription = "Python IDE for professional developers. Save time while PyCharm takes care of the routine. " +  \
         "Focus on bigger things and embrace the keyboard-centric approach to get the most of PyCharm’s many productivity features."
      }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmCommunityMacDistributionCustomizer(projectHome)
  }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) {
    "pycharm-ce"
  }
}

class PyCharmCommunityWindowsDistributionCustomizer extends PyCharmWindowsDistributionCustomizer {
  PyCharmCommunityWindowsDistributionCustomizer(String projectHome) {
    icoPath = "$projectHome/python/resources/PyCharmCore.ico"
    icoPathForEAP = "$projectHome/python/resources/PyCharmCore_EAP.ico"
    installerImagesPath = "$projectHome/python/build/resources"
    fileAssociations = ["py"]
  }

  @Override
  String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) {
    "PyCharm Community Edition"
  }
}

class PyCharmCommunityLinuxDistributionCustomizer extends LinuxDistributionCustomizer {
  PyCharmCommunityLinuxDistributionCustomizer(projectHome) {
    iconPngPath = "$projectHome/python/resources/PyCharmCore128.png"
    iconPngPathForEAP = "$projectHome/python/resources/PyCharmCore128_EAP.png"
  }

  @Override
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharm-community-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
  }
}

class PyCharmCommunityMacDistributionCustomizer extends PyCharmMacDistributionCustomizer {
  PyCharmCommunityMacDistributionCustomizer(projectHome) {
    icnsPath = "$projectHome/python/resources/PyCharmCore.icns"
    icnsPathForEAP = "$projectHome/python/resources/PyCharmCore_EAP.icns"
    bundleIdentifier = "com.jetbrains.pycharm"
    dmgImagePath = "$projectHome/python/build/dmg_background.tiff"
  }

  @Override
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    String suffix = applicationInfo.isEAP ? " ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} EAP" : ""
    "PyCharm CE${suffix}.app"
  }
}
