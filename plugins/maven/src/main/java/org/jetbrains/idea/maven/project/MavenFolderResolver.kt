// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest
import org.jetbrains.idea.maven.server.MavenGoalExecutionResult
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

@ApiStatus.Internal
class MavenFolderResolver(private val project: Project) {
  private val projectsManager: MavenProjectsManager = MavenProjectsManager.getInstance(project)

  suspend fun resolveFoldersAndImport() = resolveFoldersAndImport(projectsManager.projects)

  suspend fun resolveFoldersAndImport(projects: Collection<MavenProject>) {
    withBackgroundProgress(project, MavenProjectBundle.message("maven.updating.folders"), true) {
      withRawProgressReporter {
        doResolveFoldersAndImport(projects, rawProgressReporter!!)
      }
    }
  }

  private suspend fun doResolveFoldersAndImport(projects: Collection<MavenProject>, progressReporter: RawProgressReporter) {

    val allProjectsWithChanges = resolveFolders(projects, progressReporter)
    val projectsToImportWithChanges = allProjectsWithChanges.filter { !it.key.hasReadingProblems() && it.value.hasChanges() }

    //actually a fix for https://youtrack.jetbrains.com/issue/IDEA-286455 to be rewritten, see IDEA-294209
    MavenUtil.restartMavenConnectors(project, false) { c: MavenServerConnector ->
      val sdk = c.jdk
      val version = sdk.versionString ?: return@restartMavenConnectors false
      if (JavaVersion.parse(version).isAtLeast(17)) return@restartMavenConnectors true
      false
    }

    if (!projectsToImportWithChanges.isEmpty()) {
      projectsManager.importMavenProjects(projectsToImportWithChanges)
    }
  }

  suspend fun resolveFolders(mavenProjects: Collection<MavenProject>,
                             progressReporter: RawProgressReporter): Map<MavenProject, MavenProjectChanges> {
    val tree = projectsManager.projectsTree
    val mavenProjectsToResolve = collectMavenProjectsToResolve(mavenProjects, tree)
    val projectMultiMap = MavenUtil.groupByBasedir(mavenProjectsToResolve, tree)
    val projectsWithChanges = mutableMapOf<MavenProject, MavenProjectChanges>()
    for ((baseDir, mavenProjectsForBaseDir) in projectMultiMap.entrySet()) {
      val chunk = resolveFolders(baseDir, mavenProjectsForBaseDir, tree, progressReporter)
      projectsWithChanges.putAll(chunk)
    }
    return projectsWithChanges
  }

  private suspend fun resolveFolders(baseDir: String,
                                     mavenProjects: Collection<MavenProject>,
                                     tree: MavenProjectsTree,
                                     progressReporter: RawProgressReporter): Map<MavenProject, MavenProjectChanges> {
    val goal = projectsManager.importingSettings.updateFoldersOnImportPhase

    val fileToProject = mavenProjects.associateBy({ File(it.file.path) }, { it })
    val requests = fileToProject.entries.map { (key, value): Map.Entry<File, MavenProject> ->
      MavenGoalExecutionRequest(key, value.activatedProfilesIds)
    }

    val goalResults: List<MavenGoalExecutionResult>
    val embedder: MavenEmbedderWrapper = projectsManager.embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, baseDir)
    try {
      goalResults = embedder.executeGoal(requests, goal, progressReporter, projectsManager.syncConsole)
    }
    finally {
      projectsManager.embeddersManager.release(embedder)
    }

    val projectsWithChanges = mutableMapOf<MavenProject, MavenProjectChanges>()
    for (goalResult in goalResults) {
      val mavenProject = fileToProject.getOrDefault(goalResult.file, null)
      if (null != mavenProject && MavenUtil.shouldResetDependenciesAndFolders(goalResult.problems)) {
        val changes = mavenProject.setFolders(goalResult.folders)
        projectsWithChanges[mavenProject] = changes
        tree.fireFoldersResolved(Pair.create(mavenProject, changes))
      }
    }
    return projectsWithChanges
  }

  private fun collectMavenProjectsToResolve(mavenProjects: Collection<MavenProject>, tree: MavenProjectsTree): Collection<MavenProject> {
    // if we generate sources for the aggregator of a project, sources will be generated for the project too
    return if (Registry.`is`("maven.server.generate.sources.for.aggregator.projects")) {
      tree.collectAggregators(mavenProjects)
    }
    else mavenProjects
  }
}
