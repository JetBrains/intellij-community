// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator
import org.jetbrains.idea.maven.server.PluginResolutionRequest
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

class MavenPluginResolver(private val myTree: MavenProjectsTree) {
  private val myProject: Project = myTree.project

  suspend fun resolvePlugins(
    mavenProjectsToResolvePlugins: Collection<MavenProject>,
    embeddersManager: MavenEmbeddersManager,
    process: RawProgressReporter,
    eventHandler: MavenEventHandler) {
    val mavenProjects = mavenProjectsToResolvePlugins.filter {
      !it.hasUnrecoverableReadingProblems()
      && it.hasUnresolvedPlugins()
    }

    if (mavenProjects.isEmpty()) return

    val firstProject = sortAndGetFirst(mavenProjects)
    val baseDir = MavenUtil.getBaseDir(firstProject.directoryFile).toString()
    process.text(MavenProjectBundle.message("maven.downloading.pom.plugins", firstProject.displayName))
    val embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_PLUGINS_RESOLVE, baseDir)
    val filesToRefresh: MutableSet<Path> = HashSet()
    try {
      val mavenPluginIdsToResolve = collectMavenPluginIdsToResolve(mavenProjects)
      val mavenPluginIds = mavenPluginIdsToResolve.map { it.first }
      MavenLog.LOG.info("maven plugin resolution started: $mavenPluginIds")
      val forceUpdate = MavenProjectsManager.getInstance(myProject).forceUpdateSnapshots
      val resolutionRequests = mavenPluginIdsToResolve.map { PluginResolutionRequest(it.first, it.second.remotePluginRepositories, false, emptyList()) }
      val resolutionResults = embedder.resolvePlugins(resolutionRequests, process, eventHandler, forceUpdate)
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
      reportUnresolvedPlugins(unresolvedPluginIds)
      val pluginIdsToArtifacts = resolutionResults.associate { it.mavenPluginId to it.pluginArtifact }
      for (mavenProject in mavenProjects) {
        mavenProject.updatePluginArtifacts(pluginIdsToArtifacts)
        myTree.firePluginsResolved(mavenProject)
      }
    }
    finally {
      if (filesToRefresh.size > 0) {
        LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
      }
      embeddersManager.release(embedder)
    }
  }

  private fun reportUnresolvedPlugins(unresolvedPluginIds: Set<MavenId>) {
    if (!unresolvedPluginIds.isEmpty()) {
      for (mavenPluginId in unresolvedPluginIds) {
        MavenProjectsManager.getInstance(myProject).syncConsole
          .showArtifactBuildIssue(MavenServerConsoleIndicator.ResolveType.PLUGIN, mavenPluginId.key, null)
      }
    }
  }

  companion object {
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
}
