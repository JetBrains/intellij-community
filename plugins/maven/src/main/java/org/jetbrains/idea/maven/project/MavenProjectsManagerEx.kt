// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.impl.isTrusted
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.maven.buildtool.MavenDownloadConsole
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.importing.importActivityStarted
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter
import org.jetbrains.idea.maven.server.MavenWrapperDownloader
import org.jetbrains.idea.maven.server.showUntrustedProjectNotification
import org.jetbrains.idea.maven.utils.MavenActivityKey
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

@ApiStatus.Experimental
interface MavenAsyncProjectsManager {
  fun scheduleUpdateAllMavenProjects(spec: MavenImportSpec)
  suspend fun updateAllMavenProjects(spec: MavenImportSpec)

  fun scheduleForceUpdateMavenProject(mavenProject: MavenProject) =
    scheduleForceUpdateMavenProjects(listOf(mavenProject))

  fun scheduleForceUpdateMavenProjects(mavenProjects: List<MavenProject>) =
    scheduleUpdateMavenProjects(MavenImportSpec.EXPLICIT_IMPORT, mavenProjects.map { it.file }, emptyList())

  fun scheduleUpdateMavenProjects(spec: MavenImportSpec,
                                  filesToUpdate: List<VirtualFile>,
                                  filesToDelete: List<VirtualFile>)

  suspend fun updateMavenProjects(spec: MavenImportSpec,
                                  filesToUpdate: List<VirtualFile>,
                                  filesToDelete: List<VirtualFile>)

  @ApiStatus.Internal
  suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>)

  suspend fun downloadArtifacts(projects: Collection<MavenProject>,
                                artifacts: Collection<MavenArtifact>?,
                                sources: Boolean,
                                docs: Boolean): MavenArtifactDownloader.DownloadResult

  fun scheduleDownloadArtifacts(projects: Collection<MavenProject>,
                                artifacts: Collection<MavenArtifact>?,
                                sources: Boolean,
                                docs: Boolean)

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

  override suspend fun importMavenProjects(projectsToImport: Map<MavenProject, MavenProjectChanges>) {
    reapplyModelStructureOnly {
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
        ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).importStarted(myProject)
        val importResult = runImportProjectActivity(projectsToImport, modelsProvider, parentActivity)
        ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).importFinished(
          myProject, projectsToImport.keys, importResult.createdModules)

        importResult
      }
    }

    getVirtualFileManager().asyncRefresh()

    withBackgroundProgress(project, MavenProjectBundle.message("maven.post.processing"), true) {
      blockingContext {
        val indicator = EmptyProgressIndicator()
        for (task in importResult.postTasks) {
          task.perform(myProject, embeddersManager, indicator)
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

  private inline fun <T> reapplyModelStructureOnly(action: (StructuredIdeActivity) -> T): T {
    val syncActivity = importActivityStarted(project, MavenUtil.SYSTEM_ID, ProjectImportCollector.REAPPLY_MODEL_ACTIVITY)
    try {
      return action(syncActivity)
    }
    finally {
      syncActivity.finished {
        listOf(ProjectImportCollector.LINKED_PROJECTS.with(projectsTree.rootProjects.count()),
               ProjectImportCollector.SUBMODULES_COUNT.with(projectsTree.projects.count()))
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
                                           filesToDelete: List<VirtualFile>) {
    importMutex.withLock {
      MavenLog.LOG.warn(
        "updateMavenProjects started: ${spec.isForceReading} ${spec.isForceResolve} ${spec.isExplicitImport} ${filesToUpdate.size} ${filesToDelete.size}")
      doUpdateMavenProjects(spec, filesToUpdate, filesToDelete)
      MavenLog.LOG.warn(
        "updateMavenProjects finished: ${spec.isForceReading} ${spec.isForceResolve} ${spec.isExplicitImport} ${filesToUpdate.size} ${filesToDelete.size}")
    }
  }

  private suspend fun doUpdateMavenProjects(spec: MavenImportSpec,
                                            filesToUpdate: List<VirtualFile>,
                                            filesToDelete: List<VirtualFile>): List<Module> {
    return doUpdateMavenProjects(spec, null) { readMavenProjects(spec, filesToUpdate, filesToDelete) }
  }

  private suspend fun readMavenProjects(spec: MavenImportSpec,
                                        filesToUpdate: List<VirtualFile>,
                                        filesToDelete: List<VirtualFile>): MavenProjectsTreeUpdateResult {
    return withRawProgressReporter {
      val progressReporter = rawProgressReporter!!
      val deleted = projectsTree.delete(filesToDelete, generalSettings, progressReporter)
      val updated = projectsTree.update(filesToUpdate, spec.isForceReading, generalSettings, progressReporter)
      deleted + updated
    }
  }

  @Deprecated("Use {@link #scheduleUpdateAllMavenProjects(List)}}")
  override fun updateAllMavenProjectsSync(spec: MavenImportSpec): List<Module> {
    MavenLog.LOG.warn("updateAllMavenProjectsSync started, edt=" + ApplicationManager.getApplication().isDispatchThread)
    try {
      // unit tests
      if (ApplicationManager.getApplication().isDispatchThread) {
        return runWithModalProgressBlocking(project, MavenProjectBundle.message("maven.reading")) {
          updateAllMavenProjects(spec, null)
        }
      }
      else {
        return runBlockingMaybeCancellable {
          updateAllMavenProjects(spec, null)
        }
      }
    }
    finally {
      MavenLog.LOG.warn("updateAllMavenProjectsSync finished, edt=" + ApplicationManager.getApplication().isDispatchThread)
    }
  }

  private val importMutex = Mutex()

  override fun scheduleUpdateAllMavenProjects(spec: MavenImportSpec) {
    cs.launch {
      project.trackActivity(MavenActivityKey) {
        updateAllMavenProjects(spec)
      }
    }
  }

  override suspend fun updateAllMavenProjects(spec: MavenImportSpec) {
    updateAllMavenProjects(spec, null)
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
                                            read: suspend () -> MavenProjectsTreeUpdateResult): List<Module> {
    // display all import activities using the same build progress
    logDebug("Start update ${project.name}, ${spec.isForceReading}, ${spec.isForceResolve}, ${spec.isExplicitImport}")
    ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).syncStarted(myProject)

    MavenSyncConsole.startTransaction(myProject)
    val syncActivity = importActivityStarted(project, MavenUtil.SYSTEM_ID)
    try {


      if (spec.isForceResolve) {

        val console = syncConsole
        console.startImport(spec)
        if (MavenUtil.enablePreimport()) {
          val result = MavenProjectStaticImporter.getInstance(myProject)
            .syncStatic(
              projectsTree.existingManagedFiles,
              modelsProvider,
              importingSettings,
              generalSettings,
              !project.isTrusted(),
              syncActivity)
          if (MavenUtil.enablePreimportOnly()) return result.modules

          if (!project.isTrusted()) {
            projectsTree.updater().copyFrom(result.projectTree)
            showUntrustedProjectNotification(myProject)
            return result.modules
          }
        }
        val readingResult = readMavenProjectsActivity(syncActivity) { read() }

        fireImportAndResolveScheduled()
        val projectsToResolve = collectProjectsToResolve(readingResult)

        val result = importModules(syncActivity, projectsToResolve, modelsProvider)

        MavenResolveResultProblemProcessor.notifyMavenProblems(myProject)

        return result
      }
      else {
        val readingResult = readMavenProjectsActivity(syncActivity) { read() }
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
        listOf(ProjectImportCollector.LINKED_PROJECTS.with(projectsTree.rootProjects.count()),
               ProjectImportCollector.SUBMODULES_COUNT.with(projectsTree.projects.count()))
      }
      ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).syncFinished(myProject)
    }
  }

  private suspend fun importModules(syncActivity: StructuredIdeActivity,
                                    projectsToResolve: Collection<MavenProject>,
                                    modelsProvider: IdeModifiableModelsProvider?): List<Module> {
    logDebug("importModules started: ${projectsToResolve.size}")
    val resolver = MavenProjectResolver.getInstance(project)
    val resolutionResult = withBackgroundProgress(myProject, MavenProjectBundle.message("maven.resolving"), true) {
      withRawProgressReporter {
        runMavenImportActivity(project, syncActivity, MavenImportStats.ResolvingTask) {
          resolver.resolve(projectsToResolve, projectsTree, generalSettings, embeddersManager, rawProgressReporter!!, syncConsole)
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
          project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pluginResolutionStarted()
          runMavenImportActivity(project, MavenImportStats.PluginsResolvingTask) {
            for (mavenProjects in resolutionResult.mavenProjectMap) {
              try {
                pluginResolver.resolvePlugins(mavenProjects.value,
                                              embeddersManager,
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
    val artifactDownloadJob = doScheduleDownloadArtifacts(projectsToImport.map { it.key },
                                                          null,
                                                          importingSettings.isDownloadSourcesAutomatically,
                                                          importingSettings.isDownloadDocsAutomatically)

    val createdModules = importMavenProjects(projectsToImport, modelsProvider, syncActivity)

    pluginResolutionJob.join()
    artifactDownloadJob.join()

    return createdModules
  }

  private suspend fun readMavenProjectsActivity(parentActivity: StructuredIdeActivity,
                                                read: suspend () -> MavenProjectsTreeUpdateResult): MavenProjectsTreeUpdateResult {
    return withBackgroundProgress(myProject, MavenProjectBundle.message("maven.reading"), false) {
      runMavenImportActivity(project, parentActivity, MavenImportStats.ReadingTask) {
        project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pomReadingStarted()
        val result = read()
        project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pomReadingFinished()
        result
      }
    }
  }

  private suspend fun readAllMavenProjects(spec: MavenImportSpec): MavenProjectsTreeUpdateResult {
    return withRawProgressReporter {
      projectsTree.updateAll(spec.isForceReading, generalSettings, rawProgressReporter!!)
    }
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
    if (!MavenUtil.isWrapper(generalSettings)) return
    if (!myProject.isTrusted()) {
      showUntrustedProjectNotification(myProject)
      return
    }
    val baseDir = readAction {
      if (projectsTree.existingManagedFiles.size != 1) null else MavenUtil.getBaseDir(projectsTree.existingManagedFiles[0])
    }
    if (null == baseDir) return
    withContext(Dispatchers.IO) {
      MavenWrapperDownloader.checkOrInstallForSync(project, baseDir.toString())
    }
  }

  override fun scheduleDownloadArtifacts(projects: Collection<MavenProject>,
                                         artifacts: Collection<MavenArtifact>?,
                                         sources: Boolean,
                                         docs: Boolean) {
    doScheduleDownloadArtifacts(projects, artifacts, sources, docs)
  }

  private fun doScheduleDownloadArtifacts(projects: Collection<MavenProject>,
                                          artifacts: Collection<MavenArtifact>?,
                                          sources: Boolean,
                                          docs: Boolean): Job {
    return cs.launch {
      if (!sources && !docs) return@launch

      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingScheduled()

      downloadArtifacts(projects, artifacts, sources, docs)
    }
  }

  override suspend fun downloadArtifacts(projects: Collection<MavenProject>,
                                         artifacts: Collection<MavenArtifact>?,
                                         sources: Boolean,
                                         docs: Boolean): MavenArtifactDownloader.DownloadResult {
    if (!sources && !docs) return MavenArtifactDownloader.DownloadResult()

    val result = withBackgroundProgress(myProject, MavenProjectBundle.message("maven.downloading"), true) {
      withRawProgressReporter {
        doDownloadArtifacts(projects, artifacts, sources, docs, rawProgressReporter!!)
      }
    }

    withContext(Dispatchers.EDT) { getVirtualFileManager().asyncRefresh() }

    return result
  }

  private suspend fun doDownloadArtifacts(projects: Collection<MavenProject>,
                                          artifacts: Collection<MavenArtifact>?,
                                          sources: Boolean,
                                          docs: Boolean,
                                          progressReporter: RawProgressReporter): MavenArtifactDownloader.DownloadResult {
    project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingStarted()
    val downloadConsole = MavenDownloadConsole(project, sources, docs)
    try {
      downloadConsole.start()
      downloadConsole.startDownloadTask(projects, artifacts)
      val downloader = MavenArtifactDownloader(project, projectsTree, artifacts, progressReporter, downloadConsole)
      val result = downloader.downloadSourcesAndJavadocs(projects, sources, docs, embeddersManager)
      downloadConsole.finishDownloadTask(projects, artifacts)
      return result
    }
    catch (e: Exception) {
      downloadConsole.addException(e)
      return MavenArtifactDownloader.DownloadResult()
    }
    finally {
      downloadConsole.finish()
      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingFinished()
    }
  }

  private suspend fun <T> runMavenImportActivity(project: Project,
                                                 parentActivity: StructuredIdeActivity,
                                                 task: MavenImportStats.MavenSyncSubstask,
                                                 action: suspend () -> T): T {
    val taskClass = task::class.java
    logDebug("Import activity started: ${taskClass.simpleName}")
    val activity = task.activity.startedWithParent(project, parentActivity)
    try {
      return action()
    }
    finally {
      activity.finished()
      logDebug("Import activity finished: ${taskClass.simpleName}, result: ${resultSummary(activity)}")
    }

  }

  private suspend fun <T> runMavenImportActivity(project: Project,
                                                 task: MavenImportStats.MavenBackgroundActivitySubstask,
                                                 action: suspend () -> T): T {
    val taskClass = task::class.java
    logDebug("Import activity started: ${taskClass.simpleName}")
    val activity = task.activity.started(project)
    try {
      return action()
    }
    finally {
      activity.finished()
      logDebug("Import activity finished: ${taskClass.simpleName}, result: ${resultSummary(activity)}")
    }

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
  override suspend fun execute(project: Project) = project.trackActivity(MavenActivityKey) {
    blockingContext {
      MavenProjectsManager.getInstance(project).onProjectStartup()
    }
  }
}