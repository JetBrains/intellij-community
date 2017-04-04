/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * @author vlan
 */
abstract class PythonPluginPropertiesBase extends PyCharmPropertiesBase {
  List<String> communityModules = [
    "IntelliLang-python",
    "ipnb",
    "python-openapi",
    "python-community-plugin-core",
    "python-community-plugin-java",
    "python-community-configure",
    "python-community-plugin-minor",
    "python-psi-api",
    "python-pydev",
    "python-community",
  ]

  String pythonCommunityPluginModule = "python-community-plugin-resources"

  PythonPluginPropertiesBase() {
    super()
  }

  PluginLayout pythonCommunityPluginLayout(String pluginVersion, @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    def pluginXmlModules = [
      "IntelliLang-python",
      "ipnb",
    ]
    pythonPlugin(pythonCommunityPluginModule, "python-ce", "python-community-plugin-build-patches",
                 communityModules, pluginVersion) {
      withProjectLibrary("markdown4j-2.2")  // Required for ipnb
      pluginXmlModules.each { module ->
        excludeFromModule(module, "META-INF/plugin.xml")
      }
      excludeFromModule(pythonCommunityPluginModule, "META-INF/python-plugin-dependencies.xml")
      body.delegate = delegate
      body()
    }
  }

  static PluginLayout pythonPlugin(String mainModuleName, String name, String buildPatchesModule, List<String> modules,
                                   String pluginVersion, @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    return PluginLayout.plugin(mainModuleName) {
      directoryName = name
      mainJarName = "${name}.jar"
      version = pluginVersion
      modules.each { module ->
        withModule(module, mainJarName, false)
      }
      withModule(buildPatchesModule, mainJarName, false)
      withResourceFromModule("python-helpers", "", "helpers")
      doNotCreateSeparateJarForLocalizableResources()
      body.delegate = delegate
      body()
    }
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    return null
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return null
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return null
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return null
  }
}
