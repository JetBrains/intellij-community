// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.productLayout.*
import java.nio.file.Files
import java.nio.file.Path

open class PyCharmCommunityProperties(protected val communityHome: Path) : PyCharmPropertiesBase(enlargeWelcomeScreen = true) {
  override val customProductCode: String
    get() = "PC"

  init {
    platformPrefix = "PyCharmCore"
    applicationInfoModule = "intellij.pycharm.community"
    brandingResourcePaths = listOf(communityHome.resolve("python/resources"))
    customJvmMemoryOptions = persistentMapOf("-Xms" to "256m", "-Xmx" to "1500m")
    scrambleMainJar = false
    buildSourcesArchive = true

    productLayout.productApiModules = listOf()
    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.pycharm.community",
      "intellij.platform.whatsNew",
    )
    productLayout.bundledPluginModules +=
      sequenceOf(
        "intellij.python.community.plugin", // Python language
        "intellij.pycharm.community.customization", // Convert Intellij to PyCharm
        "intellij.pycharm.community.customization.shared",
        "intellij.vcs.github",
        "intellij.vcs.gitlab") +
      Files.readAllLines(communityHome.resolve("python/build/plugin-list.txt"))

    productLayout.skipUnresolvedContentModules = true
    
    baseDownloadUrl = "https://download.jetbrains.com/python/"

    mavenArtifacts.forIdeModules = true
    additionalVmOptions = persistentListOf("-Dllm.show.ai.promotion.window.on.start=false")
    qodanaProductProperties = QodanaProductProperties(@Suppress("SpellCheckingInspection") "QDPYC", "Qodana Community for Python")
  }

  override val moduleSetsProviders: List<ModuleSetProvider>
    get() = listOf(CommunityModuleSets)

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    // Module capability aliases
    alias("com.intellij.modules.pycharm.community")
    alias("com.intellij.modules.python-core-capable")
    alias("com.intellij.platform.ide.provisioner")

    // Content modules
    module("intellij.platform.ide.newUiOnboarding")
    module("intellij.ide.startup.importSettings")
    module("intellij.platform.tips")

    // Module sets
    moduleSet(CommunityModuleSets.ideCommon())
    moduleSet(CommunityModuleSets.rdCommon())

    // PyCharm Core fragment (includes platformLangBaseFragment, module aliases, and pycharm-core.xml)
    include(CommunityProductFragments.pycharmCoreFragment())

    // Static includes
    deprecatedInclude("intellij.platform.extended.community.impl", "META-INF/community-extensions.xml", ultimateOnly = true)
    deprecatedInclude("intellij.pycharm.community", "META-INF/pycharm-core-customization.xml")
  }

  override suspend fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
    super.copyAdditionalFiles(targetDir, context)

    val licenseTargetDir = targetDir.resolve("license")
    copyFileToDir(context.paths.communityHomeDir.resolve("LICENSE.txt"), licenseTargetDir)
    copyFileToDir(context.paths.communityHomeDir.resolve("NOTICE.txt"), licenseTargetDir)
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "PyCharmCE${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "pycharmPC-$buildNumber"

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer = object : WindowsDistributionCustomizer() {
    init {
      icoPath = communityHome.resolve("python/build/resources/PyCharmCore.ico")
      icoPathForEAP = communityHome.resolve("python/build/resources/PyCharmCore_EAP.ico")
      installerImagesPath = communityHome.resolve("python/build/resources")
    }

    override val fileAssociations: List<String>
      get() = listOf("py")

    override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties) = "PyCharm Community Edition"

    override suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
      super.copyAdditionalFiles(targetDir, arch, context)
      PyCharmBuildUtils.copySkeletons(context, targetDir, "skeletons-win*.zip")
    }

    override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String {
      return "https://www.jetbrains.com/pycharm/uninstall/?version=${appInfo.productCode}-${appInfo.majorVersion}.${appInfo.minorVersion}"
    }
  }

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer = PyCharmMacDistributionCustomizer(communityHome)

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : LinuxDistributionCustomizer() {
      init {
        iconPngPath = "${communityHome}/python/build/resources/PyCharmCore128.png"
        iconPngPathForEAP = "${communityHome}/python/build/resources/PyCharmCore128_EAP.png"
        snapName = "pycharm-community"
        snapDescription =
          "Python IDE for professional developers. Save time while PyCharm takes care of the routine. " +
          "Focus on bigger things and embrace the keyboard-centric approach to get the most of PyCharm’s many productivity features."
      }

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
        return "pycharm-community-${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"
      }
    }
  }

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "pycharm-ce"
}
