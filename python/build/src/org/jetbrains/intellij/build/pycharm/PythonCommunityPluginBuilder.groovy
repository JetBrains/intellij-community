// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import groovy.io.FileType
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildContextImpl

import java.nio.file.Path

/**
 * @author vlan
 */
@SuppressWarnings("unused")
final class PythonCommunityPluginBuilder {
  private final String home

  PythonCommunityPluginBuilder(String home) {
    this.home = home
  }

  void build() {
    String pluginBuildNumber = System.getProperty("build.number", "SNAPSHOT")
    Path homeDir = Path.of(home)
    def options = new BuildOptions(buildNumber: pluginBuildNumber,
                                   outputRootPath: homeDir.resolve("out/pycharmCE"))

    BuildDependenciesCommunityRoot communityRoot = new BuildDependenciesCommunityRoot(homeDir)
    def buildContext = BuildContextImpl.createContextBlocking(communityRoot,
                                                              homeDir,
                                                              new IdeaCommunityProperties(communityRoot),
                                                              ProprietaryBuildTools.DUMMY,
                                                              options)
    BuildTasks.create(buildContext).blockingBuildNonBundledPlugins(List.of(
      "intellij.python.community.plugin",
      "intellij.reStructuredText",
      ))

    List<File> builtPlugins = []
    buildContext.paths.artifactDir.resolve("${buildContext.applicationInfo.productCode}-plugins").toFile().eachFileRecurse(FileType.FILES) {
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
