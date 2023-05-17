// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.lang.JavaVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.project.MavenEmbeddersManager.EmbedderTask
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.function.Supplier

@ApiStatus.Internal
class MavenFolderResolver(private val project: Project) {
  private val projectsManager: MavenProjectsManager = MavenProjectsManager.getInstance(project)

  fun resolveFoldersAndImport() = resolveFoldersAndImport(projectsManager.projects)

  fun resolveFoldersAndImport(projects: Collection<MavenProject>) {
    val cs = CoroutineScope(SupervisorJob())
    cs.launch {
      val taskCancellation = TaskCancellation.cancellable()
      withBackgroundProgress(project, MavenProjectBundle.message("maven.updating.folders"), taskCancellation) {
        resolveFoldersAndImportBlocking(projects)
      }
    }
  }

  fun resolveFoldersAndImportBlocking(projects: Collection<MavenProject>) {
    if (MavenUtil.isLinearImportEnabled()) {
      MavenImportingManager.getInstance(project).resolveFolders(projects)
      return
    }

    resolveFoldersBlocking(projects)

    //actually a fix for https://youtrack.jetbrains.com/issue/IDEA-286455 to be rewritten, see IDEA-294209
    MavenUtil.restartMavenConnectors(project, false) { c: MavenServerConnector ->
      val sdk = c.jdk
      val version = sdk.versionString ?: return@restartMavenConnectors false
      if (JavaVersion.parse(version).isAtLeast(17)) return@restartMavenConnectors true
      false
    }

    if (projectsManager.hasScheduledProjects()) {
      projectsManager.importProjects()
      //myProjectsManager.fireProjectImportCompleted()
    }
  }

  fun resolveFoldersBlocking(mavenProjects: Collection<MavenProject>) {
    val tree = projectsManager.projectsTree
    val mavenProjectsToResolve = collectMavenProjectsToResolve(mavenProjects, tree)
    val projectMultiMap = MavenUtil.groupByBasedir(mavenProjectsToResolve, tree)
    for ((baseDir, mavenProjectsForBaseDir) in projectMultiMap.entrySet()) {
      resolveFoldersBlocking(baseDir, mavenProjectsForBaseDir, tree)
    }
  }

  private fun resolveFoldersBlocking(baseDir: String, mavenProjects: Collection<MavenProject>, tree: MavenProjectsTree) {
    val syncConsoleSupplier = Supplier { projectsManager.syncConsole }
    val indicator = MavenProgressIndicator(project, syncConsoleSupplier)
    val goal = projectsManager.importingSettings.updateFoldersOnImportPhase
    val task = EmbedderTask { embedder ->
      indicator.checkCanceled()
      indicator.setText(MavenProjectBundle.message("maven.updating.folders"))
      indicator.setText2("")
      val fileToProject = mavenProjects.associateBy({ File(it.file.path) }, { it })
      val requests = fileToProject.entries.map { (key, value): Map.Entry<File, MavenProject> ->
        MavenGoalExecutionRequest(key, value.activatedProfilesIds)
      }
      val results = embedder.executeGoal(requests, goal, indicator, mavenConsole)
      for (result in results) {
        val mavenProject = fileToProject.getOrDefault(result.file, null)
        if (null != mavenProject && MavenUtil.shouldResetDependenciesAndFolders(result.problems)) {
          val changes = mavenProject.setFolders(result.folders)
          tree.fireFoldersResolved(Pair.create(mavenProject, changes))
        }
      }
    }
    projectsManager.embeddersManager.execute(baseDir, MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, task)
  }

  private fun collectMavenProjectsToResolve(mavenProjects: Collection<MavenProject>, tree: MavenProjectsTree): Collection<MavenProject> {
    // if we generate sources for the aggregator of a project, sources will be generated for the project too
    return if (Registry.`is`("maven.server.generate.sources.for.aggregator.projects")) {
      tree.collectAggregators(mavenProjects)
    }
    else mavenProjects
  }

  private val mavenConsole: MavenConsole
    get() {
      val mavenGeneralSettings = projectsManager.generalSettings
      return BTWMavenConsole(project, mavenGeneralSettings.outputLevel, mavenGeneralSettings.isPrintErrorStackTraces)
    }
}
