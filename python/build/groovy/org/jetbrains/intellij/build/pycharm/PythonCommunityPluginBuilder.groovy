// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import groovy.io.FileType
import org.jetbrains.intellij.build.*

/**
 * @author vlan
 */
@SuppressWarnings("unused")
final class PythonCommunityPluginBuilder {
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
