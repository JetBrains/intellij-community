// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.IncompleteDependenciesService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.maven.buildtool.MavenDownloadConsole
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.buildtool.incrementalMode
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.importing.importActivityStarted
import org.jetbrains.idea.maven.importing.runMavenConfigurationTask
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenWorkspaceMap
import org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter
import org.jetbrains.idea.maven.project.preimport.SimpleStructureProjectVisitor
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.MavenWrapperDownloader
import org.jetbrains.idea.maven.server.showUntrustedProjectNotification
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.MavenActivityKey
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.nio.file.Files

@ApiStatus.Experimental
interface MavenAsyncProjectsManager {
  fun scheduleUpdateAllMavenProjects(spec: MavenSyncSpec)
  suspend fun updateAllMavenProjects(spec: MavenSyncSpec)

  fun scheduleForceUpdateMavenProject(mavenProject: MavenProject): Unit =
    scheduleForceUpdateMavenProjects(listOf(mavenProject))

  fun scheduleForceUpdateMavenProjects(mavenProjects: List<MavenProject>): Unit =
    scheduleUpdateMavenProjects(
      MavenSyncSpec.full("MavenProjectsManagerEx.scheduleForceUpdateMavenProjects", true),
      mavenProjects.map { it.file },
      emptyList())

  fun scheduleUpdateMavenProjects(
    spec: MavenSyncSpec,
    filesToUpdate: List<VirtualFile>,
    filesToDelete: List<VirtualFile>,
  )

  suspend fun updateMavenProjects(
    spec: MavenSyncSpec,
    filesToUpdate: List<VirtualFile>,
    filesToDelete: List<VirtualFile>,
  )

  @ApiStatus.Internal
  suspend fun importMavenProjects(projectsToImport: List<MavenProject>)

  suspend fun downloadArtifacts(
    projects: Collection<MavenProject>,
    artifacts: Collection<MavenArtifact>?,
    sources: Boolean,
    docs: Boolean,
  ): ArtifactDownloadResult

  fun scheduleDownloadArtifacts(
    projects: Collection<MavenProject>,
    artifacts: Collection<MavenArtifact>?,
    sources: Boolean,
    docs: Boolean,
  )

  @ApiStatus.Internal
  suspend fun addManagedFilesWithProfiles(
    files: List<VirtualFile>,
    profiles: MavenExplicitProfiles,
    modelsProvider: IdeModifiableModelsProvider?,
    previewModule: Module?,
    syncProject: Boolean,
  ): List<Module>

  fun projectFileExists(file: File): Boolean {
    return Files.exists(file.toPath())
  }

  suspend fun onProjectStartup()
}

open class MavenProjectsManagerEx(project: Project, private val cs: CoroutineScope) : MavenProjectsManager(project, cs) {
  override suspend fun addManagedFilesWithProfiles(
    files: List<VirtualFile>,
    profiles: MavenExplicitProfiles,
    modelsProvider: IdeModifiableModelsProvider?,
    previewModule: Module?,
    syncProject: Boolean,
  ): List<Module> {
    doAddManagedFilesWithProfiles(files, profiles, previewModule)
    if (!syncProject) return emptyList()
    return updateAllMavenProjects(MavenSyncSpec.incremental("MavenProjectsManagerEx.addManagedFilesWithProfilesAndUpdate"), modelsProvider)
  }

  override suspend fun importMavenProjects(projects: List<MavenProject>) {
    reapplyModelStructureOnly {
      importMavenProjects(projects, null, it)
    }
  }

  private suspend fun importMavenProjects(
    projectsToImport: List<MavenProject>,
    modelsProvider: IdeModifiableModelsProvider?,
    parentActivity: StructuredIdeActivity,
  ): List<Module> {
    return tracer.spanBuilder("importMavenProjects").useWithScope {
      val createdModules = doImportMavenProjects(projectsToImport, modelsProvider, parentActivity)
      fireProjectImportCompleted()
      createdModules
    }
  }

  @RequiresBackgroundThread
  private suspend fun doImportMavenProjects(
    projectsToImport: List<MavenProject>,
    optionalModelsProvider: IdeModifiableModelsProvider?,
    parentActivity: StructuredIdeActivity,
  ): List<Module> {
    val modelsProvider = optionalModelsProvider ?: ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)

    val importResult = withBackgroundProgressTraced(
      project,
      "doImportMavenProjects",
      MavenProjectBundle.message("maven.project.importing"),
      false
    ) {
      ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).importStarted(myProject)
      val importResult = runImportProjectActivity(projectsToImport, modelsProvider, parentActivity)
      tracer.spanBuilder("importFinished").use {
        ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC)
          .importFinished(myProject, projectsToImport, importResult.createdModules)
      }

      importResult
    }

    getVirtualFileManager().asyncRefresh()

    withBackgroundProgressTraced(project, "configureMavenProject", MavenProjectBundle.message("maven.post.processing"), true) {
      val indicator = EmptyProgressIndicator()
      for (task in importResult.postTasks) {
        tracer.spanBuilder("configureMavenProjectTask: ${task.javaClass.canonicalName}").use { span ->
          runMavenConfigurationTask(project, parentActivity, task.javaClass) {
            task.perform(myProject, embeddersManager, indicator)
          }
        }
      }
    }

    for (mavenProject in projectsToImport) {
      mavenProject.resetCache()
    }

    return importResult.createdModules
  }

  private suspend fun runImportProjectActivity(
    projectsToImport: List<MavenProject>,
    modelsProvider: IdeModifiableModelsProvider,
    parentActivity: StructuredIdeActivity,
  ): ImportResult {
    val projectImporter = MavenProjectImporter.createImporter(
      project, projectsTree, projectsToImport,
      modelsProvider, importingSettings, myPreviewModule, parentActivity
    )
    val postTasks = tracer.spanBuilder("importProject").useWithScope {
      projectImporter.importProject()
    }
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

  @Deprecated("Use {@link #scheduleForceUpdateMavenProjects(List)}}")
  override fun doForceUpdateProjects(projects: Collection<MavenProject>): AsyncPromise<Void> {
    val promise = AsyncPromise<Void>()
    project.trackActivityBlocking(MavenActivityKey) {
      cs.launchTracked {
        updateMavenProjects(MavenSyncSpec.full("MavenProjectsManagerEx.doForceUpdateProjects"), projects.map { it.file }, emptyList())
        promise.setResult(null)
      }
    }
    return promise
  }

  override fun scheduleUpdateMavenProjects(
    spec: MavenSyncSpec,
    filesToUpdate: List<VirtualFile>,
    filesToDelete: List<VirtualFile>,
  ) {
    project.trackActivityBlocking(MavenActivityKey) {
      cs.launchTracked { updateMavenProjects(spec, filesToUpdate, filesToDelete) }
    }
  }

  override suspend fun updateMavenProjects(
    spec: MavenSyncSpec,
    filesToUpdate: List<VirtualFile>,
    filesToDelete: List<VirtualFile>,
  ) {

    updateMavenProjectsUnderLock {
      return@updateMavenProjectsUnderLock tracer.spanBuilder("updateMavenProjects").useWithScope {
        MavenLog.LOG.warn("updateMavenProjects started: $spec ${filesToUpdate.size} ${filesToDelete.size} ${myProject.name}")
        doUpdateMavenProjects(spec, filesToUpdate, filesToDelete)
        MavenLog.LOG.warn("updateMavenProjects finished: $spec ${filesToUpdate.size} ${filesToDelete.size} ${myProject.name}")
        return@useWithScope emptyList<Module>()
      }
    }
  }

  private suspend fun doUpdateMavenProjects(
    spec: MavenSyncSpec,
    filesToUpdate: List<VirtualFile>,
    filesToDelete: List<VirtualFile>,
  ): List<Module> {
    val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    mavenEmbedderWrappers.use {
      return doUpdateMavenProjects(spec, null, mavenEmbedderWrappers) { readMavenProjects(spec, filesToUpdate, filesToDelete, mavenEmbedderWrappers) }
    }
  }

  private suspend fun readMavenProjects(
    spec: MavenSyncSpec,
    filesToUpdate: List<VirtualFile>,
    filesToDelete: List<VirtualFile>,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
  ): MavenProjectsTreeUpdateResult {
    return reportRawProgress { reporter ->
      val progressReporter = reporter
      val deleted = projectsTree.delete(filesToDelete, generalSettings, mavenEmbedderWrappers, progressReporter)
      val updated = projectsTree.update(filesToUpdate, spec.forceReading(), generalSettings, mavenEmbedderWrappers, progressReporter)
      deleted + updated
    }
  }

  @Deprecated("Use {@link #scheduleUpdateAllMavenProjects(List)}}")
  override fun updateAllMavenProjectsSync(): List<Module> {
    MavenLog.LOG.warn("updateAllMavenProjectsSync started, edt=" + ApplicationManager.getApplication().isDispatchThread)
    val spec = MavenSyncSpec.full("MavenProjectsManagerEx.updateAllMavenProjectsSync")
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

  override fun scheduleUpdateAllMavenProjects(spec: MavenSyncSpec) {
    project.trackActivityBlocking(MavenActivityKey) {
      cs.launchTracked {
        updateAllMavenProjects(spec)
      }
    }
  }

  override suspend fun updateAllMavenProjects(spec: MavenSyncSpec) {
    updateAllMavenProjects(spec, null)
  }

  private suspend fun updateAllMavenProjects(
    spec: MavenSyncSpec,
    modelsProvider: IdeModifiableModelsProvider?,
  ): List<Module> {
    return updateMavenProjectsUnderLock {
      tracer.spanBuilder("updateAllMavenProjects").useWithScope {
        MavenLog.LOG.warn("updateAllMavenProjects started: $spec ${myProject.name}")
        val result = doUpdateAllMavenProjects(spec, modelsProvider)
        MavenLog.LOG.warn("updateAllMavenProjects finished: $spec ${myProject.name}")
        result
      }
    }
  }

  private suspend fun <T> updateMavenProjectsUnderLock(update: suspend () -> List<T>): List<T> {
    val time = System.currentTimeMillis()
    if (MavenLog.LOG.isDebugEnabled) {
      MavenLog.LOG.debug("Update maven requested in $time. Coroutines dump: ${dumpCoroutines()}")
    }

    importMutex.withLock {
      MavenLog.LOG.debug("Update maven started.Time = $time")
      return update()
    }
  }

  private suspend fun doUpdateAllMavenProjects(
    spec: MavenSyncSpec,
    modelsProvider: IdeModifiableModelsProvider?,
  ): List<Module> {
    MavenSettingsCache.getInstance(myProject).reloadAsync()
    MavenDistributionsCache.getInstance(myProject).cleanCaches()
    tracer.spanBuilder("checkOrInstallMavenWrapper").useWithScope {
      checkOrInstallMavenWrapper(project)
    }
    val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    mavenEmbedderWrappers.use {
      return doUpdateMavenProjects(spec, modelsProvider, mavenEmbedderWrappers) { readAllMavenProjects(spec, mavenEmbedderWrappers) }
    }
  }

  protected open suspend fun doUpdateMavenProjects(
    spec: MavenSyncSpec,
    modelsProvider: IdeModifiableModelsProvider?,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    read: suspend () -> MavenProjectsTreeUpdateResult,
  ): List<Module> {
    return tracer.spanBuilder("syncMavenProject").useWithScope {
      // display all import activities using the same build progress
      logDebug("Start update ${project.name}, $spec ${myProject.name}")
      ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).syncStarted(myProject)

      val console = syncConsole
      console.startTransaction()
      var incompleteState: IncompleteDependenciesService.IncompleteDependenciesAccessToken? = null
      val syncActivity = importActivityStarted(project, MavenUtil.SYSTEM_ID)
      try {
        console.startImport(spec.isExplicit)
        if (MavenUtil.enablePreimport()) {
          tracer.spanBuilder("doStaticSync").useWithScope {
            val result = MavenProjectStaticImporter.getInstance(myProject)
              .syncStatic(
                projectsTree.existingManagedFiles,
                modelsProvider,
                importingSettings,
                generalSettings,
                !TrustedProjects.isProjectTrusted(project),
                SimpleStructureProjectVisitor(),
                syncActivity,
                true)
            if (MavenUtil.enablePreimportOnly()) return@useWithScope result.modules

            if (!TrustedProjects.isProjectTrusted(project)) {
              projectsTree.updater().copyFrom(result.projectTree)
              showUntrustedProjectNotification(myProject)
              return@useWithScope result.modules
            }
            incompleteState = tracer.spanBuilder("enterIncompleteState").useWithScope {
              edtWriteAction {
                project.service<IncompleteDependenciesService>().enterIncompleteState(this@MavenProjectsManagerEx)
              }
            }
          }
        }
        if (!checkMavenEnvironment(spec)) {
          MavenLog.LOG.warn("Will not continue import, bad environment")
          return@useWithScope emptyList()
        }
        val result = tracer.spanBuilder("doDynamicSync").useWithScope {
          doDynamicSync(syncActivity, read, spec, modelsProvider, mavenEmbedderWrappers)
        }

        return@useWithScope result
      }
      catch (e: Throwable) {
        logImportErrorIfNotControlFlow(e)
        return@useWithScope emptyList()
      }
      finally {
        logDebug("Finish update ${project.name}, $spec ${myProject.name}")
        incompleteState?.let {
          edtWriteAction { it.finish() }
        }
        console.finishTransaction(spec.resolveIncrementally())
        syncActivity.finished {
          listOf(
            ProjectImportCollector.LINKED_PROJECTS.with(projectsTree.rootProjects.count()),
            ProjectImportCollector.SUBMODULES_COUNT.with(projectsTree.projects.count()),
            ProjectImportCollector.INCREMENTAL_MODE.with(spec.incrementalMode),
          )
        }
        tracer.spanBuilder("syncFinished").use {
          ApplicationManager.getApplication().messageBus.syncPublisher(MavenSyncListener.TOPIC).syncFinished(myProject)
        }
      }
    }
  }

  private suspend fun checkMavenEnvironment(spec: MavenSyncSpec): Boolean {
    return MavenEnvironmentChecker(syncConsole, project).checkEnvironment(projectsTree.existingManagedFiles)
  }

  private suspend fun doDynamicSync(
    syncActivity: StructuredIdeActivity,
    read: suspend () -> MavenProjectsTreeUpdateResult,
    spec: MavenSyncSpec,
    modelsProvider: IdeModifiableModelsProvider?,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
  ): List<Module> {
    val readingResult = readMavenProjectsActivity(syncActivity) { read() }

    fireImportAndResolveScheduled()
    val projectsToResolve = collectProjectsToResolve(readingResult)

    logDebug("Reading result: ${readingResult.updated.size}, ${readingResult.deleted.size}; to resolve: ${projectsToResolve.size}")

    val resolutionResult = tracer.spanBuilder("resolveProjects").useWithScope {
      resolveMavenProjects(syncActivity, projectsToResolve, spec, mavenEmbedderWrappers)
    }

    val result = tracer.spanBuilder("importModules").useWithScope {
      importModules(syncActivity, resolutionResult, modelsProvider, mavenEmbedderWrappers)
    }

    tracer.spanBuilder("notifyMavenProblems").useWithScope {
      MavenResolveResultProblemProcessor.notifyMavenProblems(myProject)
    }
    return result
  }

  protected suspend fun importModules(
    syncActivity: StructuredIdeActivity,
    resolutionResult: MavenProjectResolutionResult,
    modelsProvider: IdeModifiableModelsProvider?,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
  ): List<Module> {

    val projectsToImport = resolutionResult.mavenProjectMap.entries
      .flatMap { it.value }

    // plugins and artifacts can be resolved in parallel with import
    return coroutineScope {
      val pluginResolutionJob = launchTracked(CoroutineName("pluginResolutionJob")) {
        val pluginResolver = MavenPluginResolver(projectsTree)
        withBackgroundProgressTraced(myProject, "resolveMavenPlugins", MavenProjectBundle.message("maven.downloading.plugins"), true) {
          reportRawProgress { reporter ->
            project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pluginResolutionStarted()
            runMavenImportActivity(project, MavenImportStats.PluginsResolvingTask) {
              for (mavenProjects in resolutionResult.mavenProjectMap) {
                try {
                  tracer.spanBuilder("doResolveMavenPlugins").useWithScope {
                    pluginResolver.resolvePlugins(mavenProjects.value, mavenEmbedderWrappers, reporter, syncConsole)
                  }
                }
                catch (e: Exception) {
                  MavenLog.LOG.warn("Plugin resolution error", e)
                }
              }
              syncConsole.finishPluginResolution()
              project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pluginResolutionFinished()
            }
          }
        }
      }
      val artifactDownloadJob = doScheduleDownloadArtifacts(this,
                                                            projectsToImport,
                                                            null,
                                                            importingSettings.isDownloadSourcesAutomatically,
                                                            importingSettings.isDownloadDocsAutomatically)

      importMavenProjects(projectsToImport, modelsProvider, syncActivity)
    }

  }

  protected suspend fun resolveMavenProjects(
    syncActivity: StructuredIdeActivity,
    projectsToResolve: Collection<MavenProject>,
    spec: MavenSyncSpec,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
  ): MavenProjectResolutionResult {
    logDebug("importModules started: ${projectsToResolve.size}")
    val resolver = MavenProjectResolver(project)
    val resolutionResult = withBackgroundProgressTraced(myProject, "resolveDependencies", MavenProjectBundle.message("maven.resolving"), true) {
      reportRawProgress { reporter ->
        runMavenImportActivity(project, syncActivity, MavenImportStats.ResolvingTask) {
          project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).projectResolutionStarted(projectsToResolve)
          val res = tracer.spanBuilder("resolution").useWithScope {
            val updateSnapshots = MavenProjectsManager.getInstance(myProject).forceUpdateSnapshots || generalSettings.isAlwaysUpdateSnapshots
            resolver.resolve(spec.resolveIncrementally(),
                             projectsToResolve,
                             projectsTree,
                             getWorkspaceMap(),
                             repositoryPath,
                             updateSnapshots,
                             mavenEmbedderWrappers,
                             reporter,
                             syncConsole)
          }
          project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).projectResolutionFinished(
            res.mavenProjectMap.entries.flatMap { it.value })
          res
        }
      }
    }
    return resolutionResult
  }

  override suspend fun onProjectStartup() {
    if (!isNormalProject) return
    if (!wasMavenized()) return

    MavenSettingsCache.getInstance(myProject).reloadAsync()
    initOnProjectStartup()
  }

  protected open fun getWorkspaceMap(): MavenWorkspaceMap = projectsTree.workspaceMap

  protected suspend fun readMavenProjectsActivity(
    parentActivity: StructuredIdeActivity,
    read: suspend () -> MavenProjectsTreeUpdateResult,
  ): MavenProjectsTreeUpdateResult {
    return withBackgroundProgressTraced(myProject, "readMavenProject", MavenProjectBundle.message("maven.reading"), false) {
      runMavenImportActivity(project, parentActivity, MavenImportStats.ReadingTask) {
        project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pomReadingStarted()
        val result = read()
        project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).pomReadingFinished()
        result
      }
    }
  }

  protected suspend fun readAllMavenProjects(spec: MavenSyncSpec, mavenEmbedderWrappers: MavenEmbedderWrappers): MavenProjectsTreeUpdateResult {
    return reportRawProgress { reporter ->
      projectsTree.updateAll(spec.forceReading(), generalSettings, mavenEmbedderWrappers, reporter)
    }
  }

  protected fun collectProjectsToResolve(readingResult: MavenProjectsTreeUpdateResult): Collection<MavenProject> {
    val updated = readingResult.updated
    val deleted = readingResult.deleted

    val updatedProjects = updated.map { it.key }

    // resolve updated, theirs dependents, and dependents of deleted
    val toResolve: MutableSet<MavenProject> = HashSet(updatedProjects)
    toResolve.addAll(projectsTree.getDependentProjects(updatedProjects + deleted))

    // do not try to resolve projects with syntactic errors
    val it = toResolve.iterator()
    while (it.hasNext()) {
      val each = it.next()
      if (each.hasReadingErrors()) {
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

  protected fun logImportErrorIfNotControlFlow(e: Throwable) {
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
      MavenLog.LOG.warn(e)
    }
  }

  protected suspend fun checkOrInstallMavenWrapper(project: Project) {
    if (!MavenUtil.isWrapper(generalSettings)) return
    if (!TrustedProjects.isProjectTrusted(myProject)) {
      showUntrustedProjectNotification(myProject)
      return
    }
    val baseDir = readAction {
      if (projectsTree.existingManagedFiles.size != 1) null else MavenUtil.getBaseDir(projectsTree.existingManagedFiles[0])
    }
    if (null == baseDir) return
    withContext(Dispatchers.IO) {
      tracer.spanBuilder("checkOrInstallForSync").useWithScope {
        MavenWrapperDownloader.checkOrInstallForSync(project, baseDir.toString(), true)
      }
    }
  }

  override fun scheduleDownloadArtifacts(
    projects: Collection<MavenProject>,
    artifacts: Collection<MavenArtifact>?,
    sources: Boolean,
    docs: Boolean,
  ) {
    doScheduleDownloadArtifacts(cs, projects, artifacts, sources, docs)
  }

  private fun doScheduleDownloadArtifacts(
    coroutineScope: CoroutineScope,
    projects: Collection<MavenProject>,
    artifacts: Collection<MavenArtifact>?,
    sources: Boolean,
    docs: Boolean,
  ) {
    coroutineScope.launchTracked(CoroutineName("doScheduleDownloadArtifacts")) {
      if (!sources && !docs) return@launchTracked

      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingScheduled()

      downloadArtifacts(projects, artifacts, sources, docs)
    }
  }

  override suspend fun downloadArtifacts(
    projects: Collection<MavenProject>,
    artifacts: Collection<MavenArtifact>?,
    sources: Boolean,
    docs: Boolean,
  ): ArtifactDownloadResult {
    if (!sources && !docs) return ArtifactDownloadResult()

    val result = withBackgroundProgressTraced(myProject, "downloadArtifacts", MavenProjectBundle.message("maven.downloading"), true) {
      reportRawProgress { reporter ->
        doDownloadArtifacts(projects, artifacts, sources, docs, reporter)
      }
    }

    withContext(Dispatchers.EDT) { getVirtualFileManager().asyncRefresh() }

    return result
  }

  private suspend fun doDownloadArtifacts(
    projects: Collection<MavenProject>,
    artifacts: Collection<MavenArtifact>?,
    sources: Boolean,
    docs: Boolean,
    progressReporter: RawProgressReporter,
  ): ArtifactDownloadResult {
    project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingStarted()
    val downloadConsole = MavenDownloadConsole(project, sources, docs)
    try {
      downloadConsole.start()
      downloadConsole.startDownloadTask(projects, artifacts)
      val downloader = MavenArtifactDownloader(project, projectsTree, artifacts, progressReporter, downloadConsole)
      val result = downloader.downloadSourcesAndJavadocs(projects, sources, docs)
      downloadConsole.finishDownloadTask(projects, artifacts)
      return result
    }
    catch (e: Exception) {
      downloadConsole.addException(e)
      return ArtifactDownloadResult()
    }
    finally {
      downloadConsole.finish()
      project.messageBus.syncPublisher<MavenImportListener>(MavenImportListener.TOPIC).artifactDownloadingFinished()
    }
  }

  protected suspend fun <T> runMavenImportActivity(
    project: Project,
    parentActivity: StructuredIdeActivity,
    task: MavenImportStats.MavenSyncSubstask,
    action: suspend () -> T,
  ): T {
    val taskClass = task::class.java
    return tracer.spanBuilder("execute: ${taskClass.simpleName}").useWithScope {
      logDebug("Import activity started: ${taskClass.simpleName}")
      val activity = task.activity.startedWithParent(project, parentActivity)
      try {
        val result = action()
        logDebug("Import activity finished: ${taskClass.simpleName}, result: ${resultSummary(activity)}")
        result
      }
      finally {
        activity.finished()
      }
    }
  }

  private suspend fun <T> runMavenImportActivity(
    project: Project,
    task: MavenImportStats.MavenBackgroundActivitySubstask,
    action: suspend () -> T,
  ): T {
    val taskClass = task::class.java
    return tracer.spanBuilder("runImportActivitySubtask: ${taskClass.simpleName}").useWithScope {
      logDebug("Import activity started: ${taskClass.simpleName}")
      val activity = task.activity.started(project)
      try {
        val result = action()
        logDebug("Import activity finished: ${taskClass.simpleName}, result: ${resultSummary(activity)}")
        result
      }
      finally {
        activity.finished()
      }
    }
  }

  protected suspend fun <T> withBackgroundProgressTraced(
    project: Project,
    operationName: String,
    title: @NlsContexts.ProgressTitle String,
    cancellable: Boolean,
    action: suspend CoroutineScope.() -> T,
  ): T {
    return tracer.spanBuilder(operationName).useWithScope { withBackgroundProgress(project, title, cancellable, action) }
  }

  protected fun logDebug(debugMessage: String) {
    MavenLog.LOG.debug(debugMessage)
  }

  private fun resultSummary(result: Any?): String {
    if (null == result) return "null"
    if (result is MavenProjectsTreeUpdateResult) {
      val updated = result.updated.map { it.key }
      val deleted = result.deleted.map { it }
      return "updated ${updated}, deleted ${deleted}"
    }
    if (result is MavenProjectResolutionResult) {
      val mavenProjects = result.mavenProjectMap.flatMap { it.value }
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
  override suspend fun execute(project: Project): Unit = project.trackActivity(MavenActivityKey) {
    MavenProjectsManager.getInstance(project).onProjectStartup()
  }
}