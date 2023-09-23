// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.SyncViewManager
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.importActivityStarted
import com.intellij.openapi.externalSystem.statistics.runImportActivity
import com.intellij.openapi.externalSystem.statistics.runImportActivitySync
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.maven.buildtool.MavenDownloadConsole
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.importing.MavenImportStats.ImportingTask
import org.jetbrains.idea.maven.importing.MavenImportStats.MavenSyncProjectTask
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.server.MavenWrapperDownloader
import org.jetbrains.idea.maven.utils.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

@ApiStatus.Experimental
interface MavenAsyncProjectsManager {
  fun scheduleUpdateAllMavenProjects(spec: MavenImportSpec)
  suspend fun updateAllMavenProjects(spec: MavenImportSpec): List<Module>

  fun scheduleForceUpdateMavenProject(mavenProject: MavenProject) =
    scheduleForceUpdateMavenProjects(listOf(mavenProject))

  fun scheduleForceUpdateMavenProjects(mavenProjects: List<MavenProject>) =
    scheduleUpdateMavenProjects(MavenImportSpec.EXPLICIT_IMPORT, mavenProjects.map { it.file }, emptyList())

  fun scheduleUpdateMavenProjects(spec: MavenImportSpec,
                                  filesToUpdate: List<VirtualFile>,
                                  filesToDelete: List<VirtualFile>)

  suspend fun updateMavenProjects(spec: MavenImportSpec,
                                  filesToUpdate: List<VirtualFile>,
                                  filesToDelete: List<VirtualFile>): List<Module>

  @ApiStatus.Internal
  suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module>

  suspend fun downloadArtifacts(projects: Collection<MavenProject>,
                                artifacts: Collection<MavenArtifact>?,
                                sources: Boolean,
                                docs: Boolean): MavenArtifactDownloader.DownloadResult

  fun downloadArtifactsSync(projects: Collection<MavenProject>,
                            artifacts: Collection<MavenArtifact>?,
                            sources: Boolean,
                            docs: Boolean): MavenArtifactDownloader.DownloadResult

  @ApiStatus.Internal
  suspend fun addManagedFilesWithProfilesAndUpdate(files: List<VirtualFile>,
                                                   profiles: MavenExplicitProfiles,
                                                   modelsProvider: IdeModifiableModelsProvider?,
                                                   previewModule: Module?): List<Module>
}

open class MavenProjectsManagerEx(project: Project) : MavenProjectsManager(project) {
  private val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)

  override suspend fun addManagedFilesWithProfilesAndUpdate(files: List<VirtualFile>,
                                                            profiles: MavenExplicitProfiles,
                                                            modelsProvider: IdeModifiableModelsProvider?,
                                                            previewModule: Module?): List<Module> {
    blockingContext { doAddManagedFilesWithProfiles(files, profiles, previewModule) }
    return updateAllMavenProjects(MavenImportSpec(false, true, false), modelsProvider)
  }

  override suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>): List<Module> {
    return reapplyModelStructureOnly {
      importMavenProjects(projectsToImport, null, it)
    }
  }

  private suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>,
                                          modelsProvider: IdeModifiableModelsProvider?,
                                          parentActivity: StructuredIdeActivity): List<Module> {
    val createdModules = doImportMavenProjects(projectsToImport, modelsProvider, parentActivity)
    fireProjectImportCompleted()
    return createdModules
  }

  @RequiresBackgroundThread
  private suspend fun doImportMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>,
                                            optionalModelsProvider: IdeModifiableModelsProvider?,
                                            parentActivity: StructuredIdeActivity
  ): List<Module> {
    if (projectsToImport.any { it.key == null }) {
      throw IllegalArgumentException("Null key in projectsToImport")
    }
    val modelsProvider = optionalModelsProvider ?: ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)

    val importResult = withBackgroundProgress(project, MavenProjectBundle.message("maven.project.importing"), false) {
      blockingContext {
        project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).importStarted()
        val importResult = runImportProjectActivity(projectsToImport, modelsProvider, parentActivity)
        project.messageBus.syncPublisher(MavenImportListener.TOPIC).importFinished(projectsToImport.keys, importResult.createdModules)
        importResult
      }
    }

    getVirtualFileManager().asyncRefresh()

    withBackgroundProgress(project, MavenProjectBundle.message("maven.post.processing"), true) {
      blockingContext {
        val indicator = MavenProgressIndicator(project, Supplier { syncConsole })
        for (task in importResult.postTasks) {
          task.perform(myProject, embeddersManager, mavenConsole, indicator)
        }
      }
    }
    return importResult.createdModules
  }

  private fun runImportProjectActivity(projectsToImport: Map<MavenProject, MavenProjectChanges>,
                                       modelsProvider: IdeModifiableModelsProvider,
                                       parentActivity: StructuredIdeActivity): ImportResult {
    val projectImporter = MavenProjectImporter.createImporter(
      project, projectsTree, projectsToImport,
      modelsProvider, importingSettings, myPreviewModule, parentActivity
    )
    val postTasks = projectImporter.importProject()
    return ImportResult(projectImporter.createdModules(), postTasks ?: emptyList())
  }

  private inline fun <T> reapplyModelStructureOnly(action: (StructuredIdeActivity) -> T): T =
    runDedicatedActivity(action, MavenImportStats.MavenReapplyModelOnlyProjectTask::class.java)

  private inline fun <T> backgroundImportActivities(action: (StructuredIdeActivity) -> T) =
    runDedicatedActivity(action, MavenImportStats.MavenBackgroundActivities::class.java)

  private inline fun <T> runDedicatedActivity(action: (StructuredIdeActivity) -> T, klass: Class<*>): T {
    val syncActivity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
      listOf(ProjectImportCollector.TASK_CLASS.with(klass))
    }
    try {
      return action(syncActivity)
    }
    finally {
      syncActivity.finished {
        listOf(ProjectImportCollector.LINKED_PROJECTS.with(projectsTree.rootProjects?.count() ?: -1),
               ProjectImportCollector.SUBMODULES_COUNT.with(projectsTree.projects?.count() ?: -1))
      }
    }
  }

  private data class ImportResult(val createdModules: List<Module>, val postTasks: List<MavenProjectsProcessorTask>)

  override fun listenForSettingsChanges() {
    importingSettings.addListener(object : MavenImportingSettings.Listener {
      override fun createModuleForAggregatorsChanged() {
        cs.launch {
          reapplyModelStructureOnly {
            doImportMavenProjects(emptyMap(), null, it)
          }
        }
      }

      override fun updateAllProjectStructure() {
        cs.launch {
          importAllProjects()
        }
      }
    })
  }

  private suspend fun importAllProjects() {
    val projectsToImport = projectsTree.projects.associateBy({ it }, { MavenProjectChanges.ALL })
    importMavenProjects(projectsToImport)
  }

  @Deprecated("Use {@link #scheduleForceUpdateMavenProjects(List)}}")
  override fun doForceUpdateProjects(projects: Collection<MavenProject>): AsyncPromise<Void> {
    val promise = AsyncPromise<Void>()
    cs.launch {
      updateMavenProjects(MavenImportSpec.EXPLICIT_IMPORT, projects.map { it.file }, emptyList())
      promise.setResult(null)
    }
    return promise
  }

  override fun scheduleUpdateMavenProjects(spec: MavenImportSpec,
                                           filesToUpdate: List<VirtualFile>,
                                           filesToDelete: List<VirtualFile>) {
    cs.launch { updateMavenProjects(spec, filesToUpdate, filesToDelete) }
  }

  override suspend fun updateMavenProjects(spec: MavenImportSpec,
                                           filesToUpdate: List<VirtualFile>,
                                           filesToDelete: List<VirtualFile>): List<Module> {
    importMutex.withLock {
      MavenLog.LOG.warn(
        "updateMavenProjects started: ${spec.isForceReading} ${spec.isForceResolve} ${spec.isExplicitImport} ${filesToUpdate.size} ${filesToDelete.size}")
      val result = doUpdateMavenProjects(spec, filesToUpdate, filesToDelete)
      MavenLog.LOG.warn(
        "updateMavenProjects finished: ${spec.isForceReading} ${spec.isForceResolve} ${spec.isExplicitImport} ${filesToUpdate.size} ${filesToDelete.size}")
      return result
    }
  }

  private suspend fun doUpdateMavenProjects(spec: MavenImportSpec,
                                            filesToUpdate: List<VirtualFile>,
                                            filesToDelete: List<VirtualFile>): List<Module> {
    return doUpdateMavenProjects(spec, null) { readMavenProjects(spec, filesToUpdate, filesToDelete) }
  }

  private fun readMavenProjects(spec: MavenImportSpec,
                                filesToUpdate: List<VirtualFile>,
                                filesToDelete: List<VirtualFile>): MavenProjectsTreeUpdateResult {
    val indicator = ProgressManager.getGlobalProgressIndicator()
    val deleted = projectsTree.delete(filesToDelete, generalSettings, indicator)
    val updated = projectsTree.update(filesToUpdate, spec.isForceReading, generalSettings, indicator)
    return deleted + updated
  }

  @Deprecated("Use {@link #scheduleUpdateAllMavenProjects(List)}}")
  override fun updateAllMavenProjectsSync(spec: MavenImportSpec): List<Module> {
    MavenLog.LOG.warn("updateAllMavenProjectsSync started, edt=" + ApplicationManager.getApplication().isDispatchThread)
    try {
      // unit tests
      if (ApplicationManager.getApplication().isDispatchThread) {
        return runWithModalProgressBlocking(project, MavenProjectBundle.message("maven.reading")) {
          updateAllMavenProjects(spec)
        }
      }
      else {
        return runBlockingMaybeCancellable {
          updateAllMavenProjects(spec)
        }
      }
    }
    finally {
      MavenLog.LOG.warn("updateAllMavenProjectsSync finished, edt=" + ApplicationManager.getApplication().isDispatchThread)
    }
  }

  private val importMutex = Mutex()

  override fun scheduleUpdateAllMavenProjects(spec: MavenImportSpec) {
    cs.launch { updateAllMavenProjects(spec) }
  }

  override suspend fun updateAllMavenProjects(spec: MavenImportSpec): List<Module> {
    return updateAllMavenProjects(spec, null)
  }

  private suspend fun updateAllMavenProjects(spec: MavenImportSpec,
                                             modelsProvider: IdeModifiableModelsProvider?): List<Module> {
    importMutex.withLock {
      MavenLog.LOG.warn("updateAllMavenProjects started: ${spec.isForceReading} ${spec.isForceResolve} ${spec.isExplicitImport}")
      val result = doUpdateAllMavenProjects(spec, modelsProvider)
      MavenLog.LOG.warn("updateAllMavenProjects finished: ${spec.isForceReading} ${spec.isForceResolve} ${spec.isExplicitImport}")
      return result
    }
  }

  private suspend fun doUpdateAllMavenProjects(spec: MavenImportSpec,
                                               modelsProvider: IdeModifiableModelsProvider?): List<Module> {
    checkOrInstallMavenWrapper(project)
    return doUpdateMavenProjects(spec, modelsProvider) { readAllMavenProjects(spec) }
  }

  private suspend fun doUpdateMavenProjects(spec: MavenImportSpec,
                                            modelsProvider: IdeModifiableModelsProvider?,
                                            read: () -> MavenProjectsTreeUpdateResult): List<Module> {
    // display all import activities using the same build progress
    logDebug("Start update ${project.name}, ${spec.isForceReading}, ${spec.isForceResolve}, ${spec.isExplicitImport}")

    MavenSyncConsole.startTransaction(myProject)
    val syncActivity = importActivityStarted(project, MavenUtil.SYSTEM_ID) {
      listOf(ProjectImportCollector.TASK_CLASS.with(MavenSyncProjectTask::class.java))
    }
    try {
      val readingResult = readMavenProjectsActivity(syncActivity) { read() }

      if (spec.isForceResolve) {
        val console = syncConsole
        console.startImport(myProgressListener, spec)

        fireImportAndResolveScheduled()
        val projectsToResolve = collectProjectsToResolve(readingResult)

        val result = runMavenImportActivity(project, syncActivity, ImportingTask::class.java) {
          val resolver = MavenProjectResolver.getInstance(project)
          val resolutionResult = withBackgroundProgress(myProject, MavenProjectBundle.message("maven.resolving"), true) {
            withRawProgressReporter {
              runMavenImportActivity(project, syncActivity, MavenImportStats.ResolvingTask::class.java) {
                resolver.resolve(projectsToResolve, projectsTree, generalSettings, embeddersManager, mavenConsole, rawProgressReporter!!,
                                 syncConsole)
              }
            }
          }

          val projectsToImport = resolutionResult.mavenProjectMap.entries
            .flatMap { it.value }
            .associateBy({ it.mavenProject }, { MavenProjectChanges.ALL })

          // plugins and artifacts can be resolved in parallel with import
          val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
          val pluginResolutionJob = cs.launch {
            val pluginResolver = MavenPluginResolver(projectsTree)
            withBackgroundProgress(myProject, MavenProjectBundle.message("maven.downloading.plugins"), true) {
              withRawProgressReporter {
                backgroundImportActivities {
                  project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pluginResolutionStarted()
                  runMavenImportActivity(project, it, MavenImportStats.PluginsResolvingTask::class.java) {
                    for (mavenProjects in resolutionResult.mavenProjectMap) {
                      try {
                        pluginResolver.resolvePlugins(mavenProjects.value,
                                                      embeddersManager,
                                                      mavenConsole,
                                                      rawProgressReporter!!,
                                                      syncConsole,
                                                      true)
                      }
                      catch (e: Exception) {
                        MavenLog.LOG.warn("Plugin resolution error", e)
                      }
                    }
                    project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pluginResolutionFinished()
                  }
                }
              }
            }
          }
          pluginResolutionJobs.add(pluginResolutionJob)
          cs.launch {
            downloadArtifacts(projectsToImport.map { it.key },
                              listOf(),
                              importingSettings.isDownloadSourcesAutomatically,
                              importingSettings.isDownloadDocsAutomatically)
          }

          importMavenProjects(projectsToImport, modelsProvider, syncActivity)
        }

        MavenResolveResultProblemProcessor.notifyMavenProblems(myProject)

        return result
      }
      return emptyList()
    }
    catch (e: Throwable) {
      logImportErrorIfNotControlFlow(e)
      return emptyList()
    }
    finally {
      logDebug("Finish update ${project.name}, ${spec.isForceReading}, ${spec.isForceResolve}, ${spec.isExplicitImport}")
      MavenSyncConsole.finishTransaction(myProject)
      syncActivity.finished {
        listOf(ProjectImportCollector.LINKED_PROJECTS.with(projectsTree.rootProjects?.count() ?: -1),
               ProjectImportCollector.SUBMODULES_COUNT.with(projectsTree.projects?.count() ?: -1))
      }
    }
  }

  private val pluginResolutionJobs = JobSet()

  @TestOnly
  override fun waitForPluginResolution() {
    pluginResolutionJobs.waitFor()
  }

  private suspend fun readMavenProjectsActivity(parentActivity: StructuredIdeActivity,
                                                read: () -> MavenProjectsTreeUpdateResult): MavenProjectsTreeUpdateResult {
    return withBackgroundProgress(myProject, MavenProjectBundle.message("maven.reading"), false) {
      runMavenImportActivity(project, parentActivity, MavenProjectsProcessorReadingTask::class.java) {
        withRawProgressReporter {
          coroutineToIndicator {
            project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pomReadingStarted()
            val result = read()
            project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pomReadingFinished()
            result
          }
        }
      }
    }
  }

  private fun readAllMavenProjects(spec: MavenImportSpec): MavenProjectsTreeUpdateResult {
    val indicator = ProgressManager.getGlobalProgressIndicator()
    return projectsTree.updateAll(spec.isForceReading, generalSettings, indicator)
  }

  private fun collectProjectsToResolve(readingResult: MavenProjectsTreeUpdateResult): Collection<MavenProject> {
    val updated = readingResult.updated
    val deleted = readingResult.deleted

    val updatedProjects = updated.map { it.key }

    // resolve updated, theirs dependents, and dependents of deleted
    val toResolve: MutableSet<MavenProject> = HashSet(updatedProjects)
    toResolve.addAll(projectsTree.getDependentProjects(ContainerUtil.concat(updatedProjects, deleted)))

    // do not try to resolve projects with syntactic errors
    val it = toResolve.iterator()
    while (it.hasNext()) {
      val each = it.next()
      if (each.hasReadingProblems()) {
        syncConsole.notifyReadingProblems(each.file)
        it.remove()
      }
    }

    if (toResolve.isEmpty() && !deleted.isEmpty()) {
      val project = nonIgnoredProjects.firstOrNull()
      if (project != null) {
        toResolve.add(project)
      }
    }

    return toResolve
  }

  private fun logImportErrorIfNotControlFlow(e: Throwable) {
    if (e is ControlFlowException) {
      ExceptionUtil.rethrowAllAsUnchecked(e)
    }
    if (e is CancellationException) {
      throw e
    }
    showServerException(e)
    if (ExceptionUtil.causedBy(e, BuildIssueException::class.java)) {
      MavenLog.LOG.info(e)
    }
    else {
      MavenLog.LOG.error(e)
    }
  }

  private suspend fun checkOrInstallMavenWrapper(project: Project) {
    if (projectsTree.existingManagedFiles.size == 1) {
      val baseDir = MavenUtil.getBaseDir(projectsTree.existingManagedFiles[0])
      if (MavenUtil.isWrapper(generalSettings)) {
        withContext(Dispatchers.IO) {
          MavenWrapperDownloader.checkOrInstallForSync(project, baseDir.toString())
        }
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
      return runWithModalProgressBlocking(project, MavenProjectBundle.message("maven.downloading")) {
        downloadArtifacts(projects, artifacts, sources, docs)
      }
    }
    else {
      return runBlockingMaybeCancellable {
        downloadArtifacts(projects, artifacts, sources, docs)
      }
    }
  }

  override suspend fun downloadArtifacts(projects: Collection<MavenProject>,
                                         artifacts: Collection<MavenArtifact>?,
                                         sources: Boolean,
                                         docs: Boolean): MavenArtifactDownloader.DownloadResult {
    if (!sources && !docs) return MavenArtifactDownloader.DownloadResult()

    val result = withBackgroundProgress(myProject, MavenProjectBundle.message("maven.downloading"), true) {
      withRawProgressReporter {
        coroutineToIndicator {
          doDownloadArtifacts(projects, artifacts, sources, docs)
        }
      }
    }

    withContext(Dispatchers.EDT) { getVirtualFileManager().asyncRefresh() }

    return result
  }

  private fun doDownloadArtifacts(projects: Collection<MavenProject>,
                                  artifacts: Collection<MavenArtifact>?,
                                  sources: Boolean,
                                  docs: Boolean): MavenArtifactDownloader.DownloadResult {
    project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingStarted()
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
      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingFinished()
    }
  }

  private suspend fun <T> runMavenImportActivity(project: Project,
                                                 parentActivity: StructuredIdeActivity,
                                                 taskClass: Class<*>,
                                                 action: suspend () -> T): T {
    logDebug("Import activity started: ${taskClass.simpleName}")
    val result = runImportActivity(project, parentActivity, taskClass, action)
    logDebug("Import activity finished: ${taskClass.simpleName}, result: ${resultSummary(result)}")
    return result
  }

  private fun <T> runMavenImportActivitySync(project: Project,
                                             parentActivity: StructuredIdeActivity,
                                             taskClass: Class<*>,
                                             action: () -> T): T {
    logDebug("Import activity started: ${taskClass.simpleName}")
    val result = runImportActivitySync(project, parentActivity, taskClass, action)
    logDebug("Import activity finished: ${taskClass.simpleName}, result: ${resultSummary(result)}")
    return result
  }

  private fun logDebug(debugMessage: String) {
    MavenLog.LOG.debug(debugMessage)
  }

  private fun resultSummary(result: Any?): String {
    if (null == result) return "null"
    if (result is MavenProjectsTreeUpdateResult) {
      val updated = result.updated.map { it.key }
      val deleted = result.deleted.map { it }
      return "updated ${updated}, deleted ${deleted}"
    }
    if (result is MavenProjectResolver.MavenProjectResolutionResult) {
      val mavenProjects = result.mavenProjectMap.flatMap { it.value }.map { it.mavenProject }
      return "resolved ${mavenProjects}"
    }
    return result.toString()
  }

  private fun getVirtualFileManager(): VirtualFileManager {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return ApplicationManager.getApplication().getService(VirtualFileManager::class.java)
    }
    return VirtualFileManager.getInstance()
  }
}

class MavenProjectsManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) = project.serviceAsync<MavenInProgressService>().trackConfigurationActivity {
    blockingContext {
      MavenProjectsManager.getInstance(project).onProjectStartup()
    }
  }
}

class JobSet {
  private val jobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()

  fun add(job: Job) {
    jobs.add(job)
    job.invokeOnCompletion {
      jobs.remove(job)
    }
  }

  fun waitFor() {
    runBlocking {
      for (job in jobs)
        job.join()
    }
  }
}