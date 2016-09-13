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

  PyCharmEduProperties(String home) {
    pythonCommunityPath = new File(home, "community/python").exists() ? "$home/community/python" : "$home/python"
    productCode = "PE"
    platformPrefix = "PyCharmEdu"
    applicationInfoModule = "educational-python"
    brandingResourcePaths = ["$pythonCommunityPath/educational-python/resources"]

    productLayout.mainModule = "main_pycharm_edu"
    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + ["dom-openapi"]
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + [
      "dom-impl", "python-community", "python-community-ide-resources",
      "python-ide-community", "python-community-configure", "educational-python", "python-openapi", "python-psi-api", "platform-main"
    ]
    productLayout.bundledPluginModules = new File("$pythonCommunityPath/educational-python/build/plugin-list.txt").readLines()
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
        installerImagesPath = "$pythonCommunityPath/educational-python/build/resources"
        customNsiConfigurationFiles = [
          "$pythonCommunityPath/educational-python/build/desktop.ini",
          "$pythonCommunityPath/educational-python/build/customInstallActions.nsi"
        ]
      }

      @Override
      String fullNameIncludingEdition(ApplicationInfoProperties applicationInfo) {
        "PyCharm Edu"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        context.ant.copy(file: "$context.paths.projectHome/help/pycharm-eduhelp.jar", todir: "$targetDirectory/help",
                         failonerror: false, quiet: true)
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$pythonCommunityPath/resources/PyCharmCore128.png"
      }

      @Override
      String rootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        "pycharm-edu-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        context.ant.copy(file: "$context.paths.projectHome/help/pycharm-eduhelp.jar", todir: "$targetDirectory/help",
                         failonerror: false, quiet: true)
      }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmMacDistributionCustomizer() {
      {
        icnsPath = "$pythonCommunityPath/educational-python/resources/PyCharmEdu.icns"
        bundleIdentifier = "com.jetbrains.pycharm"
        helpId = "PE"
        dmgImagePath = "$pythonCommunityPath/educational-python/build/DMG_background.png"
      }
    }
  }

  @Override
  String outputDirectoryName(ApplicationInfoProperties applicationInfo) {
    "pycharm-edu"
  }
}