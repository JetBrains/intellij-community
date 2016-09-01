package org.jetbrains.intellij.build.pycharm.edu

import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.pycharm.PyCharmMacDistributionCustomizer
import org.jetbrains.intellij.build.pycharm.PyCharmPropertiesBase
import org.jetbrains.intellij.build.pycharm.PyCharmWindowsDistributionCustomizer

/**
 * @author nik
 */
class PyCharmEduProperties extends PyCharmPropertiesBase {
  PyCharmEduProperties(String home) {
    productCode = "PE"
    platformPrefix = "PyCharmEdu"
    applicationInfoModule = "educational-python"
    brandingResourcePaths = ["$home/community/python/educational-python/resources"]

    productLayout.mainModule = "main_pycharm_edu"
    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + ["dom-openapi"]
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + [
      "dom-impl", "python-community", "python-community-ide-resources",
      "python-ide-community", "python-community-configure", "educational-python", "python-openapi", "python-psi-api", "platform-main"
    ]
    productLayout.bundledPluginModules = new File("$home/community/python/educational-python/build/plugin-list.txt").readLines()
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
  String systemSelector(ApplicationInfoProperties applicationInfo) {
    "PyCharmEdu${applicationInfo.majorVersion}0"
  }

  @Override
  String baseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharmEDU-$buildNumber"
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new PyCharmWindowsDistributionCustomizer() {
      {
        buildZipWithBundledOracleJre = true
        installerImagesPath = "$projectHome/community/python/educational-python/build/resources"
        customNsiConfigurationFiles = [
          "$projectHome/community/python/educational-python/build/desktop.ini",
          "$projectHome/community/python/educational-python/build/customInstallActions.nsi"
        ]
      }

      @Override
      String fullNameIncludingEdition(ApplicationInfoProperties applicationInfo) {
        "PyCharm Edu"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        context.ant.copy(file: "$context.paths.projectHome/python/help/pycharm-eduhelp.jar", todir: "$targetDirectory/help", failonerror: false)
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$projectHome/community/python/resources/PyCharmCore128.png"
      }

      @Override
      String rootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        "pycharm-edu-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        context.ant.copy(file: "$context.paths.projectHome/python/help/pycharm-eduhelp.jar", todir: "$targetDirectory/help", failonerror: false)
      }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmMacDistributionCustomizer() {
      {
        icnsPath = "$projectHome/community/python/educational-python/resources/PyCharmEdu.icns"
        bundleIdentifier = "com.jetbrains.pycharm"
        helpId = "PE"
        dmgImagePath = "$projectHome/community/python/educational-python/build/DMG_background.png"
      }
    }
  }
}