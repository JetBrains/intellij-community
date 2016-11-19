/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovy.org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.UltimateLibraryLicenses
import org.jetbrains.intellij.build.UltimateRepositoryModules
import org.jetbrains.intellij.build.WindowsDistributionCustomizer

/**
 * @author nik
 */
class PyCharmProperties extends PyCharmPropertiesBase {
  PyCharmProperties(String home) {
    productCode = "PY"
    platformPrefix = "Python"
    applicationInfoModule = "python-ide"
    brandingResourcePaths = ["$home/python/resources"]
    yourkitAgentBinariesDirectoryPath = "$home/bin"
    enableYourkitAgentInEAP = true

    productLayout.platformApiModules = UltimateRepositoryModules.PLATFORM_ULTIMATE_API_MODULES + ["graph-openapi"]
    productLayout.platformImplementationModules = UltimateRepositoryModules.PLATFORM_ULTIMATE_IMPLEMENTATION_MODULES + [
      "yaml", "graph", "coverage-common", "python-community", "python-community-ide-resources", "python", "python-ide",
      "python-ide-community", "python-community-configure",
      "python-openapi", "python-psi-api", "structuralsearch", "duplicates", "duplicates-analysis", "duplicates-xml", "duplicates-xml-analysis"
    ]
    productLayout.projectLibrariesToUnpackIntoMainJar = ["LicenseDecoder", "LicenseServerAPI", "yFiles"]
    productLayout.bundledPluginModules = new File("$home/python/build/plugin-list.txt").readLines()
    productLayout.mainModules = ["main_pycharm"]
    productLayout.licenseFilesToBuildSearchableOptions = ["$home/build/idea.license", "$home/build/pycharm.license"]
    productLayout.allNonTrivialPlugins = UltimateRepositoryModules.ULTIMATE_REPOSITORY_PLUGINS

    scrambleMainJar = true
    allLibraryLicenses = UltimateLibraryLicenses.LICENSES_LIST
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "pycharmPY-$buildNumber"
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    super.copyAdditionalFiles(context, targetDirectory)
    new DebuggerEggBuilder(context).buildDebuggerEggs("$targetDirectory/debug-eggs")
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new PyCharmWindowsDistributionCustomizer() {
      {
        installerImagesPath = "$projectHome/python/build/resources"
        fileAssociations = [".py"]
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        context.ant.copy(file: "$context.paths.projectHome/python/help/pycharmhelp.jar", todir: "$targetDirectory/help", failonerror: false)
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$projectHome/python/resources/PyCharm_128.png"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        "pycharm-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        context.ant.copy(file: "$context.paths.projectHome/python/help/pycharmhelp.jar", todir: "$targetDirectory/help", failonerror: false)
      }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new PyCharmMacDistributionCustomizer() {
      {
        icnsPath = "$projectHome/python/resources/PyCharm.icns"
        bundleIdentifier = "com.jetbrains.pycharm"
        helpId = "PY"
        urlSchemes = ["pycharm"]
        dmgImagePath = "$projectHome/python/build/DMG_background.png"
        enableYourkitAgentInEAP = true
      }

      @Override
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        context.ant.unzip(src: "$context.paths.projectHome/python/help/JetBrains.PY.help.zip", dest: "$targetDirectory/Resources")
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        return super.getRootDirectoryName(applicationInfo, buildNumber)
      }
    }
  }
}