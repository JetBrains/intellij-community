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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.runBlockingCancellableUnderIndicator
import org.jetbrains.idea.maven.utils.withBackgroundProgressIfApplicable
import java.util.*
import java.util.function.Supplier

@ApiStatus.Experimental
interface MavenAsyncProjectsManager {
  suspend fun importMavenProjects(): List<Module>
  suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>
  fun importMavenProjectsSync(): List<Module>
  fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider): List<Module>
  fun importMavenProjectsSync(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>
  fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider, projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>
}

open class MavenProjectsManagerEx(project: Project) : MavenProjectsManager(project) {
  // region import maven projects
  override suspend fun importMavenProjects(): List<Module> {
    val createdModules = doImportMavenProjects(false)
    fireProjectImportCompleted()
    return createdModules
  }

  override suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module> {
    val createdModules = doImportMavenProjects(projectsToImport, false)
    fireProjectImportCompleted()
    return createdModules
  }

  override fun importMavenProjectsSync(): List<Module> {
    return importMavenProjectsSync(ProjectDataManager.getInstance().createModifiableModelsProvider(myProject))
  }

  override fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider): List<Module> {
    return prepareImporter(modelsProvider, false).importMavenProjectsBlocking()
  }

  override fun importMavenProjectsSync(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module> {
    return importMavenProjectsSync(ProjectDataManager.getInstance().createModifiableModelsProvider(myProject), projectsToImport)
  }

  override fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider, projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module> {
    return prepareImporter(modelsProvider, projectsToImport, false).importMavenProjectsBlocking()
  }

  private suspend fun doImportMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>,
                                            importModuleGroupsRequired: Boolean): List<Module> {
    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)
    return prepareImporter(modelsProvider, projectsToImport, importModuleGroupsRequired).importMavenProjects()
  }

  private suspend fun doImportMavenProjects(importModuleGroupsRequired: Boolean): List<Module> {
    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)
    return prepareImporter(modelsProvider, importModuleGroupsRequired).importMavenProjects()
  }

  private fun prepareImporter(modelsProvider: IdeModifiableModelsProvider,
                              importModuleGroupsRequired: Boolean): MavenProjectsManagerImporter {
    val projectsToImport = Collections.unmodifiableMap(LinkedHashMap(myProjectsToImport))
    projectsToImport.forEach { (key: MavenProject, value: MavenProjectChanges) ->
      myProjectsToImport.remove(key, value)
    }
    return prepareImporter(
      modelsProvider,
      projectsToImport,
      importModuleGroupsRequired
    )
  }

  private fun prepareImporter(modelsProvider: IdeModifiableModelsProvider,
                              projectsToImport: Map<MavenProject, MavenProjectChanges>,
                              importModuleGroupsRequired: Boolean): MavenProjectsManagerImporter {
    return MavenProjectsManagerImporter(
      modelsProvider,
      projectsToImport,
      importModuleGroupsRequired
    )
  }

  private data class ImportResult(val createdModules: List<Module>, val postTasks: List<MavenProjectsProcessorTask>)

  private inner class MavenProjectsManagerImporter(private val modelsProvider: IdeModifiableModelsProvider,
                                                   private val projectsToImport: Map<MavenProject, MavenProjectChanges>,
                                                   private val importModuleGroupsRequired: Boolean) {
    private val importingTitle = MavenProjectBundle.message("maven.project.importing")
    private val postProcessingTitle = MavenProjectBundle.message("maven.post.processing")

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
      val importResult = this.doImport()
      VirtualFileManager.getInstance().syncRefresh()
      performPostImportTasks(importResult.postTasks)
      return importResult.createdModules
    }

    @RequiresBackgroundThread
    suspend fun importMavenProjectsBg(): List<Module> {
      val importResult = withBackgroundProgress(project, importingTitle, false) {
        val importResult = doImport()
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
        return@withBackgroundProgress importResult
      }
      withBackgroundProgress(project, postProcessingTitle, true) {
        performPostImportTasks(importResult.postTasks)
      }
      return importResult.createdModules
    }

    fun performPostImportTasks(postTasks: List<MavenProjectsProcessorTask>) {
      val indicator = MavenProgressIndicator(project, Supplier { syncConsole })
      for (task in postTasks) {
        task.perform(myProject, embeddersManager, mavenConsole, indicator)
      }
    }

    private fun doImport(): ImportResult {
      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).importStarted()

      val importResult = this.runImportActivity()

      // do not block user too often
      myImportingQueue.restartTimer()

      val createdModules = importResult.createdModules

      project.messageBus.syncPublisher(MavenImportListener.TOPIC).importFinished(projectsToImport.keys, createdModules)

      return importResult
    }

    private fun runImportActivity(): ImportResult {
      val activity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
        listOf(ProjectImportCollector.TASK_CLASS.with(MavenImportStats.ImportingTaskOld::class.java))
      }
      try {
        val projectImporter = MavenProjectImporter.createImporter(
          project, projectsTree, projectsToImport, importModuleGroupsRequired,
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
    val projectsToImport = projectsTree.projects.associateBy({ it }, { MavenProjectChanges.ALL })
    importMavenProjects(projectsToImport)
  }

  override fun scheduleResolveSync(callback: Runnable?): AsyncPromise<List<Module>> {
    return runBlockingCancellableUnderIndicator { resolveAndImport(callback) }
  }

  private suspend fun resolveAndImport(callback: Runnable?): AsyncPromise<List<Module>> {
    val result = AsyncPromise<List<Module>>()

    val projectsToResolve = LinkedHashSet(myProjectsToResolve)
    myProjectsToResolve.removeAll(projectsToResolve)

    val resolver = MavenProjectResolver.getInstance(project)
    val indicator = MavenProgressIndicator(project, Supplier { syncConsole })

    val resolutionResult = withBackgroundProgressIfApplicable(myProject, MavenProjectBundle.message("maven.resolving"), true) {
      val activity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
        listOf(ProjectImportCollector.TASK_CLASS.with(MavenProjectsProcessorResolvingTask::class.java))
      }
      try {
        return@withBackgroundProgressIfApplicable resolver.resolve(
          projectsToResolve, projectsTree, generalSettings, embeddersManager, mavenConsole, indicator)
      }
      finally {
        activity.finished()
      }
    }

    // TODO: plugins can be resolved in parallel with import
    val pluginResolver = MavenPluginResolver(projectsTree)
    withBackgroundProgressIfApplicable(myProject, MavenProjectBundle.message("maven.downloading.plugins"), true) {
      val activity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
        listOf(ProjectImportCollector.TASK_CLASS.with(MavenProjectsProcessorPluginsResolvingTask::class.java))
      }
      try {
        for (entry in resolutionResult.projectsWithUnresolvedPlugins) {
          pluginResolver.resolvePlugins(entry.value, embeddersManager, mavenConsole, indicator, true)
        }
      }
      finally {
        activity.finished()
      }
    }

    val createdModules = importMavenProjects()
    result.setResult(createdModules)

    callback?.run()

    return result
  }

  private val mavenConsole: MavenConsole
    get() {
      return BTWMavenConsole(project, generalSettings.outputLevel, generalSettings.isPrintErrorStackTraces)
    }

}