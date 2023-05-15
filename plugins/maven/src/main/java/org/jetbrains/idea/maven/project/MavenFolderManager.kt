// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Supplier

@ApiStatus.Internal
class MavenFolderManager(private val project: Project) {
  private val projectsManager: MavenProjectsManager = MavenProjectsManager.getInstance(project)

  fun scheduleFoldersResolveForAllProjects() = scheduleFoldersResolve(projectsManager.projects)

  fun scheduleFoldersResolve(projects: Collection<MavenProject>) {
    val cs = CoroutineScope(SupervisorJob())
    cs.launch {
      val taskCancellation = TaskCancellation.cancellable()
      withBackgroundProgress(project, MavenProjectBundle.message("maven.updating.folders"), taskCancellation) {
        scheduleFoldersResolveSync(projects)
      }
    }
  }

  @RequiresBlockingContext
  fun scheduleFoldersResolveSync(projects: Collection<MavenProject>) {
    if (MavenUtil.isLinearImportEnabled()) {
      MavenImportingManager.getInstance(project).resolveFolders(projects)
      return
    }
    val onCompletion = Runnable {
      if (projectsManager.hasScheduledProjects()) {
        projectsManager.importProjects()
        //myProjectsManager.fireProjectImportCompleted()
      }
    }

    val task = MavenProjectsProcessorFoldersResolvingTask(
      projects,
      projectsManager.importingSettings,
      projectsManager.projectsTree,
      onCompletion
    )
    val syncConsoleSupplier = Supplier { projectsManager.syncConsole }
    val indicator = MavenProgressIndicator(project, syncConsoleSupplier)
    task.perform(project, projectsManager.embeddersManager, mavenConsole, indicator)
  }

  private val mavenConsole: MavenConsole
    get() {
      val mavenGeneralSettings = projectsManager.generalSettings
      return BTWMavenConsole(project, mavenGeneralSettings.outputLevel, mavenGeneralSettings.isPrintErrorStackTraces)
    }
}
