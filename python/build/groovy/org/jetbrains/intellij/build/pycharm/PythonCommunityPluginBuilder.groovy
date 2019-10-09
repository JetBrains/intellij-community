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

import groovy.io.FileType
import org.jetbrains.intellij.build.*

/**
 * @author vlan
 */
@SuppressWarnings("unused")
class PythonCommunityPluginBuilder {
  private final String home

  PythonCommunityPluginBuilder(String home) {
    this.home = home
  }

  def build() {
    def pluginBuildNumber = System.getProperty("build.number", "SNAPSHOT")
    def pluginsForIdeaCommunity = [
      "intellij.python.community.plugin",
      "intellij.reStructuredText",
    ]

    def options = new BuildOptions(buildNumber: pluginBuildNumber,
                                   outputRootPath: "$home/out/pycharmCE")
    def buildContext = BuildContext.createContext(home,
                                                  home,
                                                  new IdeaCommunityProperties(home),
                                                  ProprietaryBuildTools.DUMMY,
                                                  options)
    BuildTasks.create(buildContext).buildNonBundledPlugins(pluginsForIdeaCommunity)

    List<File> builtPlugins = []
    new File(buildContext.paths.artifacts, "${buildContext.applicationInfo.productCode}-plugins").eachFileRecurse(FileType.FILES) {
      if (it.name.endsWith(".zip")) {
        builtPlugins << it
      }
    }
    if (builtPlugins.isEmpty()) {
      buildContext.messages.warning("No plugins were built")
      return
    }

    def pluginsPaths = new File("$buildContext.paths.buildOutputRoot/plugins-paths.txt")
    pluginsPaths.text = builtPlugins.collect { it.toString() }.join("\n")
  }
}
