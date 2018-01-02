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

    productLayout.mainModules = ["main_pycharm_edu"]
    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + ["dom-openapi"]
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + [
      "dom-impl", "python-community", "python-community-ide-resources",
      "python-community-ide", "python-community-configure", "educational-python", "python-openapi", "python-psi-api", "platform-main"
    ]
    productLayout.bundledPluginModules = new File("$pythonCommunityPath/educational-python/build/plugin-list.txt").readLines()
    additionalIDEPropertiesFilePaths = ["$home/community/python/educational-python/build/pycharm-edu.properties".toString()]
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    super.copyAdditionalFiles(context, targetDirectory)
    context.ant.copy(todir: "$targetDirectory/license") {
      fileset(file: "$context.paths.communityHome/LICENSE.txt")
      fileset(file: "$context.paths.communityHome/NOTICE.txt")
    }

    def resourcesDir = new File("$pythonCommunityPath/educational-python/resources/")
    def files = resourcesDir.listFiles(new FilenameFilter() {
      @Override
      boolean accept(File dir, String name) {
        return name.matches("EduTools-[0-9.]+-[0-9.]+-[0-9.]+.zip")
      }
    })
    if (files.length == 0) {
      throw new IllegalStateException("EduTools bundled plugin is not found in $resourcesDir")
    }
    context.ant.unzip(src: files[0], dest: "$targetDirectory/plugins/")
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
        fileAssociations = [".py"]
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
        dmgImagePath = "$pythonCommunityPath/educational-python/build/DMG_background.png"
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