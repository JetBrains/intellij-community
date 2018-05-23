package org.jetbrains.intellij.build.pycharm.edu

import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.pycharm.PyCharmMacDistributionCustomizer
import org.jetbrains.intellij.build.pycharm.PyCharmPropertiesBase
import org.jetbrains.intellij.build.pycharm.PyCharmWindowsDistributionCustomizer
/**
 * @author nik
 */
class PyCharmEduProperties extends PyCharmPropertiesBase {
  private final String pythonCommunityPath
  private final String dependenciesPath

  PyCharmEduProperties(String home) {
    pythonCommunityPath = new File(home, "community/python").exists() ? "$home/community/python" : "$home/python"
    dependenciesPath = new File(home, "community/edu/dependencies").exists() ? "$home/community/edu/dependencies" : "$home/edu/dependencies"
    productCode = "PE"
    platformPrefix = "PyCharmEdu"
    applicationInfoModule = "intellij.pycharm.edu"
    brandingResourcePaths = ["$pythonCommunityPath/educational-python/resources"]

    productLayout.mainModules = ["intellij.pycharm.edu.main"]
    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + ["intellij.xml.dom"]
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + [
      "intellij.xml.dom.impl", "intellij.python.community.impl", "intellij.pycharm.community.resources",
      "intellij.pycharm.community", "intellij.python.configure", "intellij.pycharm.edu", "intellij.python.community", "intellij.python.psi", "intellij.platform.main"
    ]
    productLayout.bundledPluginModules = new File("$pythonCommunityPath/educational-python/build/plugin-list.txt").readLines()
    additionalIDEPropertiesFilePaths = ["$pythonCommunityPath/educational-python/build/pycharm-edu.properties".toString()]
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    super.copyAdditionalFiles(context, targetDirectory)
    context.ant.copy(todir: "$targetDirectory/license") {
      fileset(file: "$context.paths.communityHome/LICENSE.txt")
      fileset(file: "$context.paths.communityHome/NOTICE.txt")
    }

    EduUtils.copyEduToolsPlugin(dependenciesPath, context, targetDirectory)
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) {
    "PyCharmEdu${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharmEDU-$buildNumber"
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new PyCharmWindowsDistributionCustomizer() {
      {
        installerImagesPath = "$pythonCommunityPath/educational-python/build/resources"
        fileAssociations = ["py"]
        silentInstallationConfig = "$pythonCommunityPath/educational-python/build/silent.config"
        customNsiConfigurationFiles = [
          "$pythonCommunityPath/educational-python/build/desktop.ini",
          "$pythonCommunityPath/educational-python/build/customInstallActions.nsi"
        ]
      }

      @Override
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) {
        "PyCharm Edu"
      }

      @Override
      String getBaseDownloadUrlForJre() { "https://download.jetbrains.com/python" }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$pythonCommunityPath/educational-python/resources/PyCharmEdu128.png"
        snapName = "pycharm-educational"
        snapDescription =
          "PyCharm Edu combines interactive learning with a powerful real-world professional development tool to provide " +
          "a platform for the most effective learning and teaching experience."
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        "pycharm-edu-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
      }

    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmMacDistributionCustomizer() {
      {
        icnsPath = "$pythonCommunityPath/educational-python/resources/PyCharmEdu.icns"
        bundleIdentifier = "com.jetbrains.pycharm"
        dmgImagePath = "$pythonCommunityPath/educational-python/build/dmg_background.tiff"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        String suffix = applicationInfo.isEAP ? " ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} RC" : ""
        "PyCharm Edu${suffix}.app"
      }
    }
  }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) {
    "pycharm-edu"
  }
}