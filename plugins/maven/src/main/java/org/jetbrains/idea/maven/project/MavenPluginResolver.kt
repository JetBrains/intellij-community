// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.server.PluginResolutionRequest
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

@ApiStatus.Internal
data class PluginResolutionResult(val unresolvedPluginIds: Set<MavenId>)

@ApiStatus.Internal
interface MavenPluginResolver {
  suspend fun resolvePlugins(
    mavenProjects: Collection<MavenProject>,
    forceUpdateSnapshots: Boolean,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    process: RawProgressReporter,
    eventHandler: MavenEventHandler): PluginResolutionResult
}

private class MavenPluginResolverImpl : MavenPluginResolver {
  override suspend fun resolvePlugins(
    mavenProjects: Collection<MavenProject>,
    forceUpdateSnapshots: Boolean,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    process: RawProgressReporter,
    eventHandler: MavenEventHandler): PluginResolutionResult {

    if (mavenProjects.isEmpty()) return PluginResolutionResult(emptySet())

    val firstProject = sortAndGetFirst(mavenProjects)
    val baseDir = MavenUtil.getBaseDir(firstProject.directoryFile).toString()
    process.text(MavenProjectBundle.message("maven.downloading.pom.plugins", firstProject.displayName))
    val embedder = mavenEmbedderWrappers.getEmbedder(baseDir)
    val filesToRefresh: MutableSet<Path> = HashSet()
    try {
      val mavenPluginIdsToResolve = collectMavenPluginIdsToResolve(mavenProjects)
      val mavenPluginIds = mavenPluginIdsToResolve.map { it.first }
      MavenLog.LOG.info("maven plugin resolution started: $mavenPluginIds")
      val resolutionRequests = mavenPluginIdsToResolve.map { PluginResolutionRequest(it.first, it.second.remotePluginRepositories, false, emptyList()) }
      val resolutionResults = embedder.resolvePlugins(resolutionRequests, process, eventHandler, forceUpdateSnapshots)
      val unresolvedPluginIds = resolutionResults.filter { !it.isResolved }.map { it.mavenPluginId }.toSet()
      MavenLog.LOG.info("maven plugin resolution finished, unresolved: $unresolvedPluginIds")
      val artifacts = resolutionResults.flatMap { it.pluginDependencyArtifacts }
      for (artifact in artifacts) {
        val pluginJar = artifact.file.toPath()
        val pluginDir = pluginJar.parent
        if (pluginDir != null) {
          filesToRefresh.add(pluginDir) // Refresh both *.pom and *.jar files.
        }
      }
      val pluginIdsToArtifacts = resolutionResults.associate { it.mavenPluginId to it.pluginArtifact }
      for (mavenProject in mavenProjects) {
        mavenProject.updatePluginArtifacts(pluginIdsToArtifacts)
      }
      return PluginResolutionResult(unresolvedPluginIds)
    }
    finally {
      if (filesToRefresh.isNotEmpty()) {
        LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
      }
    }
  }

  private fun collectMavenPluginIdsToResolve(mavenProjects: Collection<MavenProject>): Collection<Pair<MavenId, MavenProject>> {
    val mavenPluginIdsToResolve = HashSet<Pair<MavenId, MavenProject>>()
    if (Registry.`is`("maven.plugins.use.cache")) {
      val pluginIdsToProjects = HashMap<MavenId, MutableList<MavenProject>>()
      for (mavenProject in mavenProjects) {
        for (mavenPlugin in mavenProject.declaredPlugins) {
          val mavenPluginId = mavenPlugin.mavenId
          pluginIdsToProjects.putIfAbsent(mavenPluginId, ArrayList())
          pluginIdsToProjects[mavenPluginId]!!.add(mavenProject)
        }
      }
      for ((key, value) in pluginIdsToProjects) {
        mavenPluginIdsToResolve.add(Pair.create(key, sortAndGetFirst(value)))
      }
    }
    else {
      for (mavenProject in mavenProjects) {
        for (mavenPlugin in mavenProject.declaredPlugins) {
          mavenPluginIdsToResolve.add(Pair.create(mavenPlugin.mavenId, mavenProject))
        }
      }
    }
    return mavenPluginIdsToResolve
  }

  private fun sortAndGetFirst(mavenProjects: Collection<MavenProject>): MavenProject {
    return mavenProjects.minBy { it.directory }
  }
}
