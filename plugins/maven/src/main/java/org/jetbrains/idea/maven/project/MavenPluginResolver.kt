// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

class MavenPluginResolver(private val myTree: MavenProjectsTree) {
  private val myProject: Project = myTree.project

  @Throws(MavenProcessCanceledException::class)
  fun resolvePlugins(mavenProjectsToResolvePlugins: Collection<MavenProjectWithHolder>,
                     embeddersManager: MavenEmbeddersManager,
                     console: MavenConsole,
                     process: MavenProgressIndicator,
                     reportUnresolvedToSyncConsole: Boolean) {
    val mavenProjects = mavenProjectsToResolvePlugins.filter {
      !it.mavenProject.hasReadingProblems()
      && it.mavenProject.hasUnresolvedPlugins()
    }
    
    if (mavenProjects.isEmpty()) return
    
    val firstProject = sortAndGetFirst(mavenProjects).mavenProject
    val baseDir = MavenUtil.getBaseDir(firstProject.directoryFile).toString()
    process.setText(MavenProjectBundle.message("maven.downloading.pom.plugins", firstProject.displayName))
    val embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_PLUGINS_RESOLVE, baseDir)
    val unresolvedPluginIds: Set<MavenId>
    val filesToRefresh: MutableSet<Path> = HashSet()
    try {
      val mavenPluginIdsToResolve = collectMavenPluginIdsToResolve(mavenProjects)
      val mavenPluginIds = mavenPluginIdsToResolve.map { it.first }
      MavenLog.LOG.warn("maven plugin resolution started: $mavenPluginIds")
      val resolutionResults = embedder.resolvePlugins(mavenPluginIdsToResolve, process, console)
      val unresolvedPlugins = resolutionResults.filter { !it.isResolved }.map { it.mavenPluginId }
      MavenLog.LOG.warn("maven plugin resolution finished, unresolved: $unresolvedPlugins")
      val artifacts = resolutionResults.flatMap { it.artifacts }
      for (artifact in artifacts) {
        val pluginJar = artifact.file.toPath()
        val pluginDir = pluginJar.parent
        if (pluginDir != null) {
          filesToRefresh.add(pluginDir) // Refresh both *.pom and *.jar files.
        }
      }
      unresolvedPluginIds = resolutionResults.filter { !it.isResolved }.map { it.mavenPluginId }.toSet()
      if (reportUnresolvedToSyncConsole) {
        reportUnresolvedPlugins(unresolvedPluginIds)
      }
      val updatedMavenProjects = mavenProjects.map { it.mavenProject }.toSet()
      for (mavenProject in updatedMavenProjects) {
        mavenProject.resetCache()
        myTree.firePluginsResolved(mavenProject)
      }
    }
    finally {
      if (filesToRefresh.size > 0) {
        LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh)
      }
      embeddersManager.release(embedder)
    }
  }

  private fun reportUnresolvedPlugins(unresolvedPluginIds: Set<MavenId>) {
    if (!unresolvedPluginIds.isEmpty()) {
      for (mavenPluginId in unresolvedPluginIds) {
        MavenProjectsManager.getInstance(myProject)
          .syncConsole.getListener(MavenServerConsoleIndicator.ResolveType.PLUGIN)
          .showArtifactBuildIssue(mavenPluginId.key, null)
      }
    }
  }

  companion object {
    private fun collectMavenPluginIdsToResolve(mavenProjects: Collection<MavenProjectWithHolder>): Collection<Pair<MavenId, NativeMavenProjectHolder>> {
      val mavenPluginIdsToResolve = HashSet<Pair<MavenId, NativeMavenProjectHolder>>()
      if (Registry.`is`("maven.plugins.use.cache")) {
        val pluginIdsToProjects = HashMap<MavenId, MutableList<MavenProjectWithHolder>>()
        for (projectData in mavenProjects) {
          val mavenProject = projectData.mavenProject
          for (mavenPlugin in mavenProject.declaredPlugins) {
            val mavenPluginId = mavenPlugin.mavenId
            pluginIdsToProjects.putIfAbsent(mavenPluginId, ArrayList())
            pluginIdsToProjects[mavenPluginId]!!.add(projectData)
          }
        }
        for ((key, value) in pluginIdsToProjects) {
          mavenPluginIdsToResolve.add(Pair.create(key, sortAndGetFirst(value).mavenProjectHolder))
        }
      }
      else {
        for (projectData in mavenProjects) {
          val mavenProject = projectData.mavenProject
          val nativeMavenProject = projectData.mavenProjectHolder
          for (mavenPlugin in mavenProject.declaredPlugins) {
            mavenPluginIdsToResolve.add(Pair.create(mavenPlugin.mavenId, nativeMavenProject))
          }
        }
      }
      return mavenPluginIdsToResolve
    }

    private fun sortAndGetFirst(mavenProjects: Collection<MavenProjectWithHolder>): MavenProjectWithHolder {
      return mavenProjects.minBy { it.mavenProject.directoryFile.path }
    }
  }
}
