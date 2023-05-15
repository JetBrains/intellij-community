// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.lang.JavaVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Supplier

@ApiStatus.Internal
class MavenFolderManager(private val project: Project) {
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

    val resolver = MavenFolderResolver()
    resolver.resolveFolders(
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
}
