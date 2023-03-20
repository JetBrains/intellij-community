// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.PluginLayout

import java.nio.file.Files
import java.nio.file.Path

class PyCharmCommunityProperties(communityHome: Path) : PyCharmPropertiesBase() {
  override val customProductCode: String
    get() = "PC"

  init {
    platformPrefix = "PyCharmCore"
    applicationInfoModule = "intellij.pycharm.community"
    brandingResourcePaths = listOf(communityHome.resolve("python/resources"))
    scrambleMainJar = false
    buildSourcesArchive = true

    productLayout.mainModules = listOf("intellij.pycharm.community.main")
    productLayout.productApiModules = listOf("intellij.xml.dom")
    productLayout.productImplementationModules = listOf(
      "intellij.xml.dom.impl",
      "intellij.platform.main",
      "intellij.pycharm.community",
    )
    productLayout.bundledPluginModules.add("intellij.python.community.plugin")
    productLayout.bundledPluginModules.add("intellij.pycharm.community.customization")
    productLayout.bundledPluginModules.addAll(Files.readAllLines(communityHome.resolve("python/build/plugin-list.txt")))

    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS.add(
      PluginLayout.plugin(listOf(
        "intellij.pycharm.community.customization",
        "intellij.pycharm.community.ide.impl",
        "intellij.jupyter.viewOnly",
        "intellij.jupyter.core"
      )
      ))
    productLayout.pluginModulesToPublish = persistentSetOf("intellij.python.community.plugin")
  }

  override fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: String) {
    super.copyAdditionalFilesBlocking(context, targetDirectory)

    FileSet(context.paths.communityHomeDir)
      .include("LICENSE.txt")
      .include("NOTICE.txt")
      .copyToDir(Path.of(targetDirectory, "license"))
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "PyCharmCE${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "pycharmPC-$buildNumber"

  override fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer {
    return PyCharmCommunityWindowsDistributionCustomizer(Path.of(projectHome))
  }

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : PyCharmCommunityLinuxDistributionCustomizer(Path.of(projectHome)) {
      init {
        snapName = "pycharm-community"
        snapDescription = "Python IDE for professional developers. Save time while PyCharm takes care of the routine. " +
         "Focus on bigger things and embrace the keyboard-centric approach to get the most of PyCharm’s many productivity features."
      }
    }
  }

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer {
    return PyCharmCommunityMacDistributionCustomizer(Path.of(projectHome))
  }

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties) = "pycharm-ce"
}

private class PyCharmCommunityWindowsDistributionCustomizer(projectHome: Path) : PyCharmWindowsDistributionCustomizer() {
  init {
    icoPath = "$projectHome/python/resources/PyCharmCore.ico"
    icoPathForEAP = "$projectHome/python/resources/PyCharmCore_EAP.ico"
    installerImagesPath = "$projectHome/python/build/resources"
    fileAssociations = listOf("py")
  }

  override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties) = "PyCharm Community Edition"
}

private open class PyCharmCommunityLinuxDistributionCustomizer(projectHome: Path) : LinuxDistributionCustomizer() {
  init {
    iconPngPath = "$projectHome/python/resources/PyCharmCore128.png"
    iconPngPathForEAP = "$projectHome/python/resources/PyCharmCore128_EAP.png"
  }

  override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "pycharm-community-${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"
  }
}

private class PyCharmCommunityMacDistributionCustomizer(projectHome: Path) : PyCharmMacDistributionCustomizer() {
  init {
    icnsPath = "$projectHome/python/resources/PyCharmCore.icns"
    icnsPathForEAP = "$projectHome/python/resources/PyCharmCore_EAP.icns"
    bundleIdentifier = "com.jetbrains.pycharm.ce"
    dmgImagePath = "$projectHome/python/build/dmg_background.tiff"
  }

  override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    val suffix = if (appInfo.isEAP) " ${appInfo.majorVersion}.${appInfo.minorVersion} EAP" else ""
    return "PyCharm CE${suffix}.app"
  }
}
