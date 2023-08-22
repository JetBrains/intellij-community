// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildContextImpl
import java.nio.file.FileVisitResult
import java.nio.file.Files

import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal class PythonCommunityPluginBuilder(private val home: Path) {
  fun build() {
    val pluginBuildNumber = System.getProperty("build.number", "SNAPSHOT")
    val homeDir = home
    val options = BuildOptions()
    options.buildNumber = pluginBuildNumber
    options.outputRootPath = homeDir.resolve("out/pycharmCE")

    val communityRoot = BuildDependenciesCommunityRoot(homeDir)
    val buildContext = BuildContextImpl.createContextBlocking(communityRoot,
                                                              homeDir,
                                                              IdeaCommunityProperties(communityRoot.communityRoot),
                                                              ProprietaryBuildTools.DUMMY,
                                                              options)
    BuildTasks.create(buildContext).blockingBuildNonBundledPlugins(listOf(
      "intellij.python.community.plugin",
      "intellij.reStructuredText",
    ))

    val builtPlugins = mutableListOf<Path>()
    Files.walkFileTree(buildContext.paths.artifactDir.resolve("${buildContext.applicationInfo.productCode}-plugins"),
                       object : SimpleFileVisitor<Path>() {
                         override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                           if (file.toString().endsWith(".zip")) {
                             builtPlugins.add(file)
                           }
                           return FileVisitResult.CONTINUE
                         }
                       })
    if (builtPlugins.isEmpty()) {
      buildContext.messages.warning("No plugins were built")
      return
    }

    val pluginsPaths = buildContext.paths.buildOutputDir.resolve("plugins-paths.txt")
    Files.createDirectories(pluginsPaths.parent)
    Files.writeString(pluginsPaths, builtPlugins.joinToString("\n"))
  }
}
