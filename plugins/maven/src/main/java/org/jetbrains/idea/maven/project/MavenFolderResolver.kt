// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
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
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

@ApiStatus.Internal
class MavenFolderResolver(private val project: Project) {
  private val projectsManager: MavenProjectsManager = MavenProjectsManager.getInstance(project)

  fun resolveFoldersForAllProjects() = resolveFolders(projectsManager.projects)

  fun resolveFolders(projects: Collection<MavenProject>) {
    val cs = CoroutineScope(SupervisorJob())
    cs.launch {
      val taskCancellation = TaskCancellation.cancellable()
      withBackgroundProgress(project, MavenProjectBundle.message("maven.updating.folders"), taskCancellation) {
        resolveFoldersBlocking(projects)
      }
    }
  }

  @RequiresBlockingContext
  fun resolveFoldersBlocking(projects: Collection<MavenProject>) {
    if (MavenUtil.isLinearImportEnabled()) {
      MavenImportingManager.getInstance(project).resolveFolders(projects)
      return
    }

    val syncConsoleSupplier = Supplier { projectsManager.syncConsole }
    val indicator = MavenProgressIndicator(project, syncConsoleSupplier)

    resolveFolders(
      projects,
      projectsManager.projectsTree,
      projectsManager.importingSettings,
      projectsManager.embeddersManager,
      mavenConsole,
      indicator
    )

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

  private val mavenConsole: MavenConsole
    get() {
      val mavenGeneralSettings = projectsManager.generalSettings
      return BTWMavenConsole(project, mavenGeneralSettings.outputLevel, mavenGeneralSettings.isPrintErrorStackTraces)
    }

  fun resolveFolders(mavenProjects: Collection<MavenProject>,
                     tree: MavenProjectsTree,
                     importingSettings: MavenImportingSettings,
                     embeddersManager: MavenEmbeddersManager,
                     console: MavenConsole,
                     process: MavenProgressIndicator) {
    val mavenProjectsToResolve = collectMavenProjectsToResolve(mavenProjects, tree)
    doResolveFolders(mavenProjectsToResolve, tree, importingSettings, embeddersManager, console, process)
  }

  private fun collectMavenProjectsToResolve(mavenProjects: Collection<MavenProject>,
                                            tree: MavenProjectsTree): Collection<MavenProject> {
    // if we generate sources for the aggregator of a project, sources will be generated for the project too
    return if (Registry.`is`("maven.server.generate.sources.for.aggregator.projects")) {
      tree.collectAggregators(mavenProjects)
    }
    else mavenProjects
  }

  private fun doResolveFolders(mavenProjects: Collection<MavenProject>,
                               tree: MavenProjectsTree,
                               importingSettings: MavenImportingSettings,
                               embeddersManager: MavenEmbeddersManager,
                               console: MavenConsole,
                               process: MavenProgressIndicator) {
    val projectMultiMap = MavenUtil.groupByBasedir(mavenProjects, tree)
    for ((baseDir, mavenProjectsForBaseDir) in projectMultiMap.entrySet()) {
      resolveFolders(baseDir,
                     mavenProjectsForBaseDir,
                     tree,
                     importingSettings,
                     embeddersManager,
                     console,
                     process)
    }
  }

  private fun resolveFolders(baseDir: String,
                             mavenProjects: Collection<MavenProject>,
                             tree: MavenProjectsTree,
                             importingSettings: MavenImportingSettings,
                             embeddersManager: MavenEmbeddersManager,
                             console: MavenConsole,
                             process: MavenProgressIndicator) {
    val goal = importingSettings.updateFoldersOnImportPhase
    val task = EmbedderTask { embedder ->
      process.checkCanceled()
      process.setText(MavenProjectBundle.message("maven.updating.folders"))
      process.setText2("")
      val fileToProject = mavenProjects.stream()
        .collect(Collectors.toMap(
          Function { mavenProject: MavenProject ->
            File(mavenProject.file.path)
          },
          Function { mavenProject: MavenProject? -> mavenProject }))
      val requests = fileToProject.entries.map { (key, value): Map.Entry<File, MavenProject?> ->
        MavenGoalExecutionRequest(key, value!!.activatedProfilesIds)
      }
      val results = embedder.executeGoal(requests, goal, process, console)
      for (result in results) {
        val mavenProject = fileToProject.getOrDefault(result.file, null)
        if (null != mavenProject && MavenUtil.shouldResetDependenciesAndFolders(result.problems)) {
          val changes = mavenProject.setFolders(result.folders)
          tree.fireFoldersResolved(Pair.create(mavenProject, changes))
        }
      }
    }
    embeddersManager.execute(baseDir, MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, task)
  }
}
