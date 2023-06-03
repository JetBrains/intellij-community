// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.SyncViewManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.importActivityStarted
import com.intellij.openapi.externalSystem.statistics.runImportActivity
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.buildtool.MavenDownloadConsole
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.server.MavenWrapperDownloader
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.runBlockingCancellableUnderIndicator
import org.jetbrains.idea.maven.utils.withBackgroundProgressIfApplicable
import java.util.*
import java.util.function.Supplier

@ApiStatus.Experimental
interface MavenAsyncProjectsManager {
  suspend fun updateAllMavenProjects(spec: MavenImportSpec)
  suspend fun importMavenProjects(): List<Module>
  suspend fun resolveAndImportMavenProjects(projects: Collection<MavenProject>): List<Module>
  fun resolveAndImportMavenProjectsSync(spec: MavenImportSpec): List<Module>
  suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>
  fun importMavenProjectsSync(): List<Module>
  fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider): List<Module>
  fun importMavenProjectsSync(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>
  fun importMavenProjectsSync(modelsProvider: IdeModifiableModelsProvider, projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>
  suspend fun downloadArtifacts(projects: Collection<MavenProject>,
                                artifacts: Collection<MavenArtifact>?,
                                sources: Boolean,
                                docs: Boolean): MavenArtifactDownloader.DownloadResult

  fun downloadArtifactsSync(projects: Collection<MavenProject>,
                            artifacts: Collection<MavenArtifact>?,
                            sources: Boolean,
                            docs: Boolean): MavenArtifactDownloader.DownloadResult
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

  override fun resolveAndImportMavenProjectsSync(spec: MavenImportSpec): List<Module> {
    return scheduleImportAndResolve(spec).blockingGet(0)!!
  }

  override suspend fun resolveAndImportMavenProjects(projects: Collection<MavenProject>): List<Module> {
    return resolveAndImport(projects)
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
      val importResult = withBackgroundProgress(project, MavenProjectBundle.message("maven.project.importing"), false) {
        val importResult = blockingContext { doImport() }
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
      withBackgroundProgress(project, MavenProjectBundle.message("maven.post.processing"), true) {
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

      val importResult = runImportProjectActivity()

      // do not block user too often
      myImportingQueue.restartTimer()

      val createdModules = importResult.createdModules

      project.messageBus.syncPublisher(MavenImportListener.TOPIC).importFinished(projectsToImport.keys, createdModules)

      return importResult
    }

    private fun runImportProjectActivity(): ImportResult {
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

  override fun scheduleImportAndResolve(spec: MavenImportSpec): Promise<List<Module>> {
    val console = syncConsole
    console.startImport(myProgressListener, spec)
    val activity = MavenImportStats.startImportActivity(myProject)
    fireImportAndResolveScheduled(spec)

    val projectsToResolve = LinkedHashSet(myProjectsToResolve)
    myProjectsToResolve.removeAll(projectsToResolve)

    val result = AsyncPromise<List<Module>>()
    val createdModules = runBlockingCancellableUnderIndicator { resolveAndImport(projectsToResolve) }
    result.setResult(createdModules)

    activity.finished()
    MavenResolveResultProblemProcessor.notifyMavenProblems(myProject)
    MavenSyncConsole.finishTransaction(myProject)

    return result
  }

  private suspend fun resolveAndImport(projectsToResolve: Collection<MavenProject>): List<Module> {
    val resolver = MavenProjectResolver.getInstance(project)

    val resolutionResult = withBackgroundProgressIfApplicable(myProject, MavenProjectBundle.message("maven.resolving"), true) {
      runImportActivity(project, MavenUtil.SYSTEM_ID, MavenProjectsProcessorResolvingTask::class.java) {
        withRawProgressReporter {
          coroutineToIndicator {
            val indicator = ProgressManager.getGlobalProgressIndicator()
            resolver.resolve(projectsToResolve, projectsTree, generalSettings, embeddersManager, mavenConsole, indicator, syncConsole)
          }
        }
      }
    }

    val projectsToImport = resolutionResult.mavenProjectMap.entries
      .flatMap { it.value }
      .filter { it.changes.hasChanges() }
      .associateBy({ it.mavenProject }, { it.changes })

    // TODO: plugins and artifacts can be resolved in parallel with import
    val indicator = MavenProgressIndicator(project, Supplier { syncConsole })
    val pluginResolver = MavenPluginResolver(projectsTree)
    withBackgroundProgressIfApplicable(myProject, MavenProjectBundle.message("maven.downloading.plugins"), true) {
      runImportActivity(project, MavenUtil.SYSTEM_ID, MavenProjectsProcessorPluginsResolvingTask::class.java) {
        withRawProgressReporter {
          coroutineToIndicator {
            for (mavenProjects in resolutionResult.mavenProjectMap) {
              pluginResolver.resolvePlugins(mavenProjects.value, embeddersManager, mavenConsole, indicator, true)
            }
          }
        }
      }
    }
    downloadArtifacts(projectsToImport.map { it.key },
                      listOf(),
                      importingSettings.isDownloadSourcesAutomatically,
                      importingSettings.isDownloadDocsAutomatically)

    return importMavenProjects(projectsToImport + myProjectsToImport)
  }

  override suspend fun updateAllMavenProjects(spec: MavenImportSpec) {
    // display all import activities using the same build progress
    MavenSyncConsole.startTransaction(myProject)
    try {
      val mavenProgressIndicator = MavenProgressIndicator(project, Supplier { syncConsole })
      withBackgroundProgressIfApplicable(myProject, SyncBundle.message("maven.sync.waiting.for.completion"), false) {
        runImportActivity(project, MavenUtil.SYSTEM_ID, MavenProjectsProcessorPluginsResolvingTask::class.java) {
          withRawProgressReporter {
            coroutineToIndicator {
              val indicator = ProgressManager.getGlobalProgressIndicator()
              try {
                checkOrInstallMavenWrapper(project)
                // TODO: use indicator
                projectsTree.updateAll(spec.isForceReading, generalSettings, mavenProgressIndicator)
                generalSettings.updateFromMavenConfig(projectsTree.rootProjectsFiles)
              }
              finally {
                if (spec.isForceResolve) {
                  resolveAndImportMavenProjectsSync(spec)
                }
              }
            }
          }
        }
      }
    }
    finally {
      if (!spec.isForceResolve) {
        MavenSyncConsole.finishTransaction(myProject)
      }
    }
  }

  private fun checkOrInstallMavenWrapper(project: Project) {
    if (projectsTree.existingManagedFiles.size == 1) {
      val baseDir = MavenUtil.getBaseDir(projectsTree.existingManagedFiles[0])
      if (MavenUtil.isWrapper(generalSettings)) {
        MavenWrapperDownloader.checkOrInstallForSync(project, baseDir.toString())
      }
    }
  }

  private val mavenConsole: MavenConsole
    get() {
      return BTWMavenConsole(project, generalSettings.outputLevel, generalSettings.isPrintErrorStackTraces)
    }

  override fun downloadArtifactsSync(projects: Collection<MavenProject>,
                                     artifacts: Collection<MavenArtifact>?,
                                     sources: Boolean,
                                     docs: Boolean): MavenArtifactDownloader.DownloadResult {
    if (ApplicationManager.getApplication().isDispatchThread) {
      return doDownloadArtifacts(projects, artifacts, sources, docs)
    }
    return runBlockingCancellableUnderIndicator { downloadArtifacts(projects, artifacts, sources, docs) }
  }

  override suspend fun downloadArtifacts(projects: Collection<MavenProject>,
                                         artifacts: Collection<MavenArtifact>?,
                                         sources: Boolean,
                                         docs: Boolean): MavenArtifactDownloader.DownloadResult {
    if (!sources && !docs) return MavenArtifactDownloader.DownloadResult()

    val result = withBackgroundProgressIfApplicable(myProject, MavenProjectBundle.message("maven.downloading"), true) {
      withRawProgressReporter {
        coroutineToIndicator {
          doDownloadArtifacts(projects, artifacts, sources, docs)
        }
      }
    }

    withContext(Dispatchers.EDT) { VirtualFileManager.getInstance().asyncRefresh() }

    return result
  }

  private fun doDownloadArtifacts(projects: Collection<MavenProject>,
                                  artifacts: Collection<MavenArtifact>?,
                                  sources: Boolean,
                                  docs: Boolean): MavenArtifactDownloader.DownloadResult {
    val indicator = ProgressManager.getGlobalProgressIndicator()
    val progressListener = project.getService(SyncViewManager::class.java)
    val downloadConsole = MavenDownloadConsole(project)
    try {
      downloadConsole.startDownload(progressListener, sources, docs)
      downloadConsole.startDownloadTask()
      val downloader = MavenArtifactDownloader(project, projectsTree, artifacts, indicator, null)
      val result = downloader.downloadSourcesAndJavadocs(projects, sources, docs, embeddersManager, mavenConsole)
      downloadConsole.finishDownloadTask()
      return result
    }
    catch (e: Exception) {
      downloadConsole.addException(e)
      return MavenArtifactDownloader.DownloadResult()
    }
    finally {
      downloadConsole.finishDownload()
    }
  }
}