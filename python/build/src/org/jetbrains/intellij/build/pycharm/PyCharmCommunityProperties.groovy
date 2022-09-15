// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot

import java.nio.file.Files
import java.nio.file.Path

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf
import static org.jetbrains.intellij.build.impl.PluginLayoutGroovy.plugin

@CompileStatic
final class PyCharmCommunityProperties extends PyCharmPropertiesBase {
  @Override
  String getCustomProductCode() {
    return "PC"
  }

  PyCharmCommunityProperties(BuildDependenciesCommunityRoot communityHome) {
    platformPrefix = "PyCharmCore"
    applicationInfoModule = "intellij.pycharm.community"
    brandingResourcePaths = List.of(communityHome.communityRoot.resolve("python/resources"))
    scrambleMainJar = false
    buildSourcesArchive = true

    productLayout.mainModules = ["intellij.pycharm.community.main"]
    productLayout.productApiModules = ["intellij.xml.dom"]
    productLayout.productImplementationModules = [
      "intellij.xml.dom.impl",
      "intellij.platform.main",
      "intellij.pycharm.community"

    ]
    productLayout.bundledPluginModules.add("intellij.python.community.plugin")
    productLayout.bundledPluginModules.add("intellij.pycharm.community.customization")
    productLayout.bundledPluginModules.addAll(Files.readAllLines(communityHome.communityRoot.resolve("python/build/plugin-list.txt")))

    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS.add(
      plugin("intellij.pycharm.community.customization") {
        directoryName = "pythonIDE"
        mainJarName = "python-ide.jar"
        withModule("intellij.pycharm.community.ide.impl", mainJarName)
        withModule("intellij.jupyter.viewOnly")
        withModule("intellij.jupyter.core")
      })
    productLayout.pluginModulesToPublish = persistentListOf("intellij.python.community.plugin")
  }

  @Override
  void copyAdditionalFilesBlocking(BuildContext context, String targetDirectory) {
    super.copyAdditionalFilesBlocking(context, targetDirectory)

    new FileSet(context.paths.communityHomeDir)
      .include("LICENSE.txt")
      .include("NOTICE.txt")
      .copyToDir(Path.of(targetDirectory, "license"))
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

@CompileStatic
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

@CompileStatic
class PyCharmCommunityLinuxDistributionCustomizer extends LinuxDistributionCustomizer {
  PyCharmCommunityLinuxDistributionCustomizer(projectHome) {
    iconPngPath = "$projectHome/python/resources/PyCharmCore128.png"
    iconPngPathForEAP = "$projectHome/python/resources/PyCharmCore128_EAP.png"
  }

  @Override
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharm-community-${applicationInfo.isEAP() ? buildNumber : applicationInfo.fullVersion}"
  }
}

@CompileStatic
class PyCharmCommunityMacDistributionCustomizer extends PyCharmMacDistributionCustomizer {
  PyCharmCommunityMacDistributionCustomizer(projectHome) {
    icnsPath = "$projectHome/python/resources/PyCharmCore.icns"
    icnsPathForEAP = "$projectHome/python/resources/PyCharmCore_EAP.icns"
    bundleIdentifier = "com.jetbrains.pycharm.ce"
    dmgImagePath = "$projectHome/python/build/dmg_background.tiff"
  }

  @Override
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    String suffix = applicationInfo.isEAP() ? " ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} EAP" : ""
    "PyCharm CE${suffix}.app"
  }
}
