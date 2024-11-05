// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyFileToDir
import java.nio.file.Files
import java.nio.file.Path

open class PyCharmCommunityProperties(private val communityHome: Path) : PyCharmPropertiesBase() {
  override val customProductCode: String
    get() = "PC"

  init {
    platformPrefix = "PyCharmCore"
    applicationInfoModule = "intellij.pycharm.community"
    brandingResourcePaths = listOf(communityHome.resolve("python/resources"))
    customJvmMemoryOptions = persistentMapOf("-Xms" to "256m", "-Xmx" to "1500m")
    scrambleMainJar = false
    buildSourcesArchive = true

    productLayout.productApiModules = listOf("intellij.xml.dom")
    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.pycharm.community",
      "intellij.platform.whatsNew"
    )
    productLayout.bundledPluginModules +=
      sequenceOf(
        "intellij.python.community.plugin", // Python language
        "intellij.pycharm.community.customization", // Convert Intellij to PyCharm
        "intellij.vcs.github.community") +
      Files.readAllLines(communityHome.resolve("python/build/plugin-list.txt"))

    productLayout.pluginModulesToPublish = persistentSetOf("intellij.python.community.plugin")
    baseDownloadUrl = "https://download.jetbrains.com/python/"

    mavenArtifacts.forIdeModules = true
    qodanaProductProperties = QodanaProductProperties("QDPYC", "Qodana Community for Python")
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) {
    super.copyAdditionalFiles(context, targetDir)

    val licenseTargetDir = targetDir.resolve("license")
    copyFileToDir(context.paths.communityHomeDir.resolve("LICENSE.txt"), licenseTargetDir)
    copyFileToDir(context.paths.communityHomeDir.resolve("NOTICE.txt"), licenseTargetDir)
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "PyCharmCE${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "pycharmPC-$buildNumber"

  override fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer {
    return PyCharmCommunityWindowsDistributionCustomizer(communityHome)
  }

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : PyCharmCommunityLinuxDistributionCustomizer(communityHome) {
      init {
        snapName = "pycharm-community"
        snapDescription = "Python IDE for professional developers. Save time while PyCharm takes care of the routine. " +
                          "Focus on bigger things and embrace the keyboard-centric approach to get the most of PyCharm’s many productivity features."
      }
    }
  }

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer {
    return PyCharmCommunityMacDistributionCustomizer(communityHome)
  }

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties) = "pycharm-ce"
}

private class PyCharmCommunityWindowsDistributionCustomizer(projectHome: Path) : PyCharmWindowsDistributionCustomizer() {
  init {
    icoPath = "$projectHome/python/build/resources/PyCharmCore.ico"
    icoPathForEAP = "$projectHome/python/build/resources/PyCharmCore_EAP.ico"
    installerImagesPath = "$projectHome/python/build/resources"
    fileAssociations = listOf("py")
  }

  override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties) = "PyCharm Community Edition"
}

private open class PyCharmCommunityLinuxDistributionCustomizer(projectHome: Path) : LinuxDistributionCustomizer() {
  init {
    iconPngPath = "$projectHome/python/build/resources/PyCharmCore128.png"
    iconPngPathForEAP = "$projectHome/python/build/resources/PyCharmCore128_EAP.png"
  }

  override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "pycharm-community-${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"
  }
}

private class PyCharmCommunityMacDistributionCustomizer(projectHome: Path) : PyCharmMacDistributionCustomizer() {
  init {
    icnsPath = "$projectHome/python/build/resources/PyCharmCore.icns"
    icnsPathForEAP = "$projectHome/python/build/resources/PyCharmCore_EAP.icns"
    bundleIdentifier = "com.jetbrains.pycharm.ce"
    dmgImagePath = "$projectHome/python/build/resources/dmg_background.tiff"
  }

  override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    val suffix = if (appInfo.isEAP) " ${appInfo.majorVersion}.${appInfo.minorVersion} EAP" else ""
    return "PyCharm CE${suffix}.app"
  }
}
