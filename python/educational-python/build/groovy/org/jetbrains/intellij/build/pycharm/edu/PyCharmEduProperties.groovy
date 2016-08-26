package org.jetbrains.intellij.build.pycharm.edu

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.CommunityLibraryLicenses
import org.jetbrains.intellij.build.CommunityRepositoryModules
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

/**
 * @author nik
 */
class PyCharmEduProperties extends ProductProperties {
  PyCharmEduProperties(String home) {
    baseFileName = "pycharm"
    productCode = "PE"
    platformPrefix = "PyCharmEdu"
    applicationInfoModule = "educational-python"
    brandingResourcePaths = ["$home/community/python/educational-python/resources"]
    additionalIDEPropertiesFilePaths = ["$home/build/conf/ideaJNC.properties"]
    reassignAltClickToMultipleCarets = true

    productLayout.mainJarName = "pycharm.jar"
    productLayout.mainModule = "main_pycharm_edu"
    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + ["dom-openapi"]
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + [
      "dom-impl", "python-community", "python-community-ide-resources",
      "python-ide-community", "python-community-configure", "educational-python", "python-openapi", "python-psi-api", "platform-main"
    ]
    productLayout.additionalPlatformJars.put("pycharm-pydev.jar", "python-pydev")
    productLayout.bundledPluginModules = new File("$home/community/python/educational-python/build/plugin-list.txt").readLines()

    allLibraryLicenses = CommunityLibraryLicenses.LICENSES_LIST
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModules(["python-pydev"], "$targetDirectory/lib/src/pycharm-pydev-src.zip")
    tasks.zipSourcesOfModules(["python-openapi", "python-psi-api"], "$targetDirectory/lib/src/pycharm-openapi-src.zip")

    context.ant.copy(todir: "$targetDirectory/helpers") {
      fileset(dir: "$context.paths.projectHome/community/python/helpers")
    }
    context.ant.copy(todir: "$targetDirectory/help") {
      fileset(dir: "$context.paths.projectHome/python/help") {
        include(name: "*.pdf")
      }
    }
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
  String environmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    "PYCHARM"
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new WindowsDistributionCustomizer() {
      {
        buildZipWithBundledOracleJre = true
        installerImagesPath = "$projectHome/community/python/educational-python/build/resources"
        customNsiConfigurationFiles = [
          "$projectHome/community/python/educational-python/build/desktop.ini",
          "$projectHome/community/python/educational-python/build/customInstallActions.nsi"
        ]
      }

      @Override
      String rootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        ""
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        context.ant.copy(todir: "$targetDirectory/skeletons") {
          fileset(dir: "$context.paths.projectHome/community/python/skeletons") {
            include(name: "skeletons-win*.zip")
          }
        }
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
    return new MacDistributionCustomizer() {
      {
        icnsPath = "$projectHome/community/python/educational-python/resources/PyCharmEdu.icns"
        bundleIdentifier = "com.jetbrains.pycharm"
        helpId = "PE"
        dmgImagePath = "$projectHome/community/python/educational-python/build/DMG_background.png"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        context.ant.copy(todir: "$targetDirectory/skeletons") {
          fileset(dir: "$context.paths.projectHome/community/python/skeletons") {
            include(name: "skeletons-mac*.zip")
          }
        }
      }

      @Override
      Map<String, String> customIdeaProperties(ApplicationInfoProperties applicationInfo) {
        ["ide.mac.useNativeClipboard": "false"]
      }
    }
  }
}