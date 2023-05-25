// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.importActivityStarted
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.runBlockingCancellableUnderIndicator
import java.util.*

@ApiStatus.Experimental
interface MavenAsyncProjectsManager {
  suspend fun importMavenProjects(): List<Module>
  suspend fun importMavenProjects(projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>): List<Module>
  fun importMavenProjectsSync(): List<Module>
  fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider): List<Module>
}

open class MavenProjectsManagerEx(project: Project, val coroutineScope: CoroutineScope) : MavenProjectsManager(project) {
  // region import maven projects
  override suspend fun importMavenProjects(): List<Module> {
    val createdModules = doImportMavenProjects(false)
    fireProjectImportCompleted()
    return createdModules
  }

  override suspend fun importMavenProjects(projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>): List<Module> {
    val createdModules = doImportMavenProjects(projectsToImportWithChanges, false)
    fireProjectImportCompleted()
    return createdModules
  }

  override fun importMavenProjectsSync(): List<Module> {
    return importMavenProjectsSync(ProjectDataManager.getInstance().createModifiableModelsProvider(myProject))
  }

  override fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider): List<Module> {
    return prepareImporter(modelsProvider, false).importMavenProjectsBlocking()
  }

  private suspend fun doImportMavenProjects(projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                                            importModuleGroupsRequired: Boolean): List<Module> {
    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)
    return prepareImporter(modelsProvider, projectsToImportWithChanges, importModuleGroupsRequired).importMavenProjects()
  }

  private suspend fun doImportMavenProjects(importModuleGroupsRequired: Boolean): List<Module> {
    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)
    return prepareImporter(modelsProvider, importModuleGroupsRequired).importMavenProjects()
  }

  private fun prepareImporter(modelsProvider: IdeModifiableModelsProvider,
                              importModuleGroupsRequired: Boolean): MavenProjectsManagerImporter {
    val projectsToImportWithChanges = Collections.unmodifiableMap(LinkedHashMap(myProjectsToImport))
    projectsToImportWithChanges.forEach { (key: MavenProject, value: MavenProjectChanges) ->
      myProjectsToImport.remove(key, value)
    }
    return prepareImporter(
      modelsProvider,
      projectsToImportWithChanges,
      importModuleGroupsRequired
    )
  }

  private fun prepareImporter(modelsProvider: IdeModifiableModelsProvider,
                              projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                              importModuleGroupsRequired: Boolean): MavenProjectsManagerImporter {
    return MavenProjectsManagerImporter(
      modelsProvider,
      projectsToImportWithChanges,
      importModuleGroupsRequired
    )
  }

  private data class ImportResult(val createdModules: List<Module>, val postTasks: List<MavenProjectsProcessorTask>)

  private inner class MavenProjectsManagerImporter(private val modelsProvider: IdeModifiableModelsProvider,
                                                   private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
                                                   private val importModuleGroupsRequired: Boolean) {
    private val title = MavenProjectBundle.message("maven.project.importing")

    @RequiresBlockingContext
    fun importMavenProjectsBlocking(): List<Module> {
      if (ApplicationManager.getApplication().isDispatchThread) {
        return importMavenProjectsEdt()
      }
      else {
        return runBlockingCancellableUnderIndicator { importMavenProjectsBg() }
      }
    }

    suspend fun importMavenProjects(): List<Module> {
      if (ApplicationManager.getApplication().isDispatchThread) {
        return importMavenProjectsEdt()
      }
      else {
        return importMavenProjectsBg()
      }
    }

    @RequiresEdt
    fun importMavenProjectsEdt(): List<Module> {
      val createdModules = this.doImport()
      VirtualFileManager.getInstance().syncRefresh()
      return createdModules
    }

    @RequiresBackgroundThread
    suspend fun importMavenProjectsBg(): List<Module> {
      return withBackgroundProgress(project, title, false) {
        val createdModules = doImport()
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

    private fun doImport(): List<Module> {
      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).importStarted()

      val importResult = this.runImportActivity()

      schedulePostImportTasks(importResult.postTasks)

      // do not block user too often
      myImportingQueue.restartTimer();

      val createdModules = importResult.createdModules

      project.messageBus.syncPublisher(MavenImportListener.TOPIC).importFinished(projectsToImportWithChanges.keys, createdModules)

      return createdModules
    }

    private fun runImportActivity(): ImportResult {
      val activity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
        listOf(ProjectImportCollector.TASK_CLASS.with(MavenImportStats.ImportingTaskOld::class.java))
      }
      try {
        val projectImporter = MavenProjectImporter.createImporter(
          project, projectsTree, projectsToImportWithChanges, importModuleGroupsRequired,
          modelsProvider, importingSettings, myPreviewModule, activity
        )
        val postTasks = projectImporter.importProject()
        return ImportResult(projectImporter.createdModules(), postTasks ?: emptyList())
      }
      finally {
        activity.finished()
      }
    }
  }
  //endregion

  override fun listenForSettingsChanges() {
    importingSettings.addListener(object : MavenImportingSettings.Listener {
      override fun createModuleGroupsChanged() {
        runBlockingCancellableUnderIndicator {
          importSettings(true)
        }
      }

      override fun createModuleForAggregatorsChanged() {
        runBlockingCancellableUnderIndicator {
          importSettings(false)
      }
      }

      override fun updateAllProjectStructure() {
        runBlockingCancellableUnderIndicator {
          importAllProjects()
        }
      }
    })
  }


  private suspend fun importSettings(importModuleGroupsRequired: Boolean) {
    doImportMavenProjects(importModuleGroupsRequired)
  }

  private suspend fun importAllProjects() {
    val projectsToImportWithChanges = projectsTree.projects.associateBy({ it }, { MavenProjectChanges.ALL })
    importMavenProjects(projectsToImportWithChanges)
  }

}