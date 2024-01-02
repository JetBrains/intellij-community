// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package org.jetbrains.intellij.build.pycharm

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildContextImpl
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal class PythonCommunityPluginBuilder(private val home: Path) {
  suspend fun build() {
    val pluginBuildNumber = System.getProperty("build.number", "SNAPSHOT")
    val homeDir = home
    val options = BuildOptions()
    options.buildNumber = pluginBuildNumber
    options.outputRootPath = homeDir.resolve("out/pycharmCE")

    val communityRoot = BuildDependenciesCommunityRoot(homeDir)
    val buildContext = BuildContextImpl.createContext(communityHome = communityRoot,
                                                      projectHome = homeDir,
                                                      productProperties = IdeaCommunityProperties(communityRoot.communityRoot),
                                                      options = options)
    BuildTasks.create(buildContext).buildNonBundledPlugins(listOf(
      "intellij.python.community.plugin",
    ))

    val builtPlugins = mutableListOf<Path>()
    withContext(Dispatchers.IO) {
      Files.walkFileTree(buildContext.paths.artifactDir.resolve("${buildContext.applicationInfo.productCode}-plugins"),
                         object : SimpleFileVisitor<Path>() {
                           override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                             if (file.toString().endsWith(".zip")) {
                               builtPlugins.add(file)
                             }
                             return FileVisitResult.CONTINUE
                           }
                         })
    }
    if (builtPlugins.isEmpty()) {
      Span.current().addEvent("No plugins were built")
      return
    }

    val pluginPaths = buildContext.paths.buildOutputDir.resolve("plugins-paths.txt")
    withContext(Dispatchers.IO) {
      Files.createDirectories(pluginPaths.parent)
      Files.writeString(pluginPaths, builtPlugins.joinToString("\n"))
    }
  }
}
