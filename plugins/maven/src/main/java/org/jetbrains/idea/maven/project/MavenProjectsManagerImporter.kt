// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.importActivityStarted
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.idea.maven.importing.MavenImportStats.ImportingTaskOld
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.createImporter
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.runBlockingCancellableUnderIndicator

internal class MavenProjectsManagerImporter(private val modelsProvider: IdeModifiableModelsProvider,
                                            private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                                            private val importModuleGroupsRequired: Boolean) {
  private val title = MavenProjectBundle.message("maven.project.importing")

  @RequiresBlockingContext
  fun MavenProjectsManager.importMavenProjectsBlocking(): List<Module> {
    val importer = this
    if (ApplicationManager.getApplication().isDispatchThread) {
      return importer.importMavenProjectsEdt()
    }
    else {
      return runBlockingCancellableUnderIndicator { importer.importMavenProjects() }
    }
  }

  @RequiresEdt
  fun MavenProjectsManager.importMavenProjectsEdt(): List<Module> {
    val createdModules = this.doImport()
    VirtualFileManager.getInstance().syncRefresh()
    return createdModules
  }

  @RequiresBackgroundThread
  suspend fun MavenProjectsManager.importMavenProjects(): List<Module> {
    val manager = this
    return withBackgroundProgress(project, title, false) {
      val createdModules = manager.doImport()
      val fm = VirtualFileManager.getInstance()
      val noBackgroundMode = MavenUtil.isNoBackgroundMode()
      val shouldKeepTasksAsynchronousInHeadlessMode = CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()
      if (noBackgroundMode && !shouldKeepTasksAsynchronousInHeadlessMode) {
        writeAction {
          fm.syncRefresh()
        }
      }
      else {
        fm.asyncRefresh()
      }
      return@withBackgroundProgress createdModules
    }
  }

  private fun MavenProjectsManager.doImport(): List<Module> {
    project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).importStarted()

    val importResult = this.runImportActivity()

    schedulePostImportTasks(importResult.postTasks)

    // do not block user too often
    restartImportingQueueTimer()

    val createdModules = importResult.createdModules

    project.messageBus.syncPublisher(MavenImportListener.TOPIC).importFinished(projectsToImportWithChanges.keys, createdModules)

    return createdModules
  }

  private data class ImportResult(val createdModules: List<Module>, val postTasks: List<MavenProjectsProcessorTask>)

  private fun MavenProjectsManager.runImportActivity(): ImportResult {
    val activity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
      listOf(ProjectImportCollector.TASK_CLASS.with(ImportingTaskOld::class.java))
    }
    try {
      val projectImporter = createImporter(
        project, projectsTree, projectsToImportWithChanges, importModuleGroupsRequired,
        modelsProvider, importingSettings, previewModule, activity
      )
      val postTasks = projectImporter.importProject()
      return ImportResult(projectImporter.createdModules(), postTasks ?: emptyList())
    }
    finally {
      activity.finished()
    }
  }
}