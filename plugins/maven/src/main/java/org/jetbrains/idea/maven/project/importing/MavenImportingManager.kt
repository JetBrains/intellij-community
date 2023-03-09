// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEventsNls
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.findPluginInfoBySystemId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.execution.RunnerBundle
import org.jetbrains.idea.maven.importing.MavenImportStats
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.TimeUnit

@ApiStatus.Experimental
class MavenImportingManager(val project: Project) {

  private val disposable = Disposer.newDisposable("Maven Importing manager")

  private val mavenPluginInfo by lazy {
    findPluginInfoBySystemId(MavenUtil.SYSTEM_ID)
  }

  var currentContext: MavenImportContext? = null
    private set(value) {
      if (ApplicationManager.getApplication().isDispatchThread) {
        field = value
      }
      else {
        ApplicationManager.getApplication().invokeAndWait {
          field = value
        }
      }

    }

  private val console by lazy {
    project.getService(MavenProjectsManager::class.java).syncConsole
  }

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Maven importing executor", 1)

  init {
    val connection: MessageBusConnection = project.messageBus.connect(disposable)
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(p: Project) {
        Disposer.dispose(disposable)
        forceStopImport()
        executor.shutdownNow()
        ProgressManager.getInstance().runProcessWithProgressSynchronously({ executor.awaitTermination(500, TimeUnit.MILLISECONDS) },
                                                                          RunnerBundle.message("maven.server.shutdown"),
                                                                          false, project)
      }
    })
  }

  private var waitingPromise = AsyncPromise<MavenImportFinishedContext>()

  @RequiresEdt
  fun linkAndImportFile(pom: VirtualFile): MavenImportingResult {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val manager = MavenProjectsManager.getInstance(project)
    val importPath = if (pom.isDirectory) RootPath(pom) else FilesList(pom)
    return openProjectAndImport(importPath, manager.importingSettings, manager.generalSettings, MavenImportSpec.EXPLICIT_IMPORT)
  }

  @RequiresEdt
  fun resolveFolders(projects: Collection<MavenProject>): Promise<Collection<MavenProject>> {
    val result = AsyncPromise<Collection<MavenProject>>()
    runUnderIndicator(MavenProjectBundle.message("maven.updating.folders")) {
      try {
        MavenImportFlow().resolveFolders(projects, project, it)
      }
      catch (e: Throwable) {
        result.setError(e)
      }
    }
    return result
  }

  @RequiresEdt
  fun openProjectAndImport(importPaths: ImportPaths): MavenImportingResult {
    val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings
    return openProjectAndImport(importPaths,
                                settings.getImportingSettings(),
                                settings.getGeneralSettings(),
                                MavenImportSpec.EXPLICIT_IMPORT)

  }

  @RequiresEdt
  fun openProjectAndImport(importPaths: ImportPaths,
                           importingSettings: MavenImportingSettings,
                           generalSettings: MavenGeneralSettings,
                           spec: MavenImportSpec): MavenImportingResult {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (isImportingInProgress()) {
      return MavenImportingResult(waitingPromise, null, null)
    }
    if (executor.isShutdown) {
      throw RuntimeException("Project is closing")
    }
    waitingPromise = AsyncPromise()
    MavenUtil.setupProjectSdk(project)
    currentContext = MavenStartedImport(project)
    val enabledProfiles = MavenProjectsManager.getInstance(project).explicitProfiles.enabledProfiles
    val disabledProfiles = MavenProjectsManager.getInstance(project).explicitProfiles.disabledProfiles
    val initialImportContext = MavenImportFlow().prepareNewImport(project, importPaths, generalSettings, importingSettings,
                                                                  enabledProfiles,
                                                                  disabledProfiles)
    project.messageBus.syncPublisher(MavenImportListener.TOPIC).importStarted(spec)

    setProjectSettings(initialImportContext)

    runUnderIndicator(MavenProjectBundle.message("maven.project.importing")) { indicator ->
      try {
        val finishedContext = doImport(indicator, initialImportContext, spec)
        getWaitingPromise().setResult(finishedContext)
      }
      catch (e: Throwable) {
        MavenLog.LOG.debug("import started at: ", initialImportContext.startImportStackTrace)
        MavenLog.LOG.warn(e)
        if (indicator.isCanceled) {
          getWaitingPromise().setError("Cancelled")
        }
        else {
          MavenLog.LOG.error(e)
          getWaitingPromise().setError(e)
        }
      }
      finally {
        Disposer.dispose(initialImportContext.importDisposable)
      }
    }

    val vfsRefreshPromise = AsyncPromise<Any?>();
    VirtualFileManager.getInstance().asyncRefresh {
      vfsRefreshPromise.setResult(null)
    }
    return MavenImportingResult(getImportFinishPromise(), vfsRefreshPromise, initialImportContext.previewModule)
  }

  private fun setProjectSettings(initialImportContext: MavenInitialImportContext) {
    //workaround, as most maven machinery use settings from component, also we need to keep listeners
    val generalSettings = MavenWorkspaceSettingsComponent.getInstance(initialImportContext.project).settings.getGeneralSettings()
    val importingSettings = MavenWorkspaceSettingsComponent.getInstance(initialImportContext.project).settings.getImportingSettings()
    MavenWorkspaceSettingsComponent.getInstance(initialImportContext.project).settings.setGeneralSettings(
      initialImportContext.generalSettings)
    MavenWorkspaceSettingsComponent.getInstance(initialImportContext.project).settings.setImportingSettings(
      initialImportContext.importingSettings)
    initialImportContext.generalSettings.copyListeners(generalSettings)
    initialImportContext.importingSettings.copyListeners(importingSettings)

    MavenWorkspaceSettingsComponent.getInstance(initialImportContext.project).settings.setGeneralSettings(
      initialImportContext.generalSettings)

  }

  private fun doImport(indicator: MavenProgressIndicator,
                       initialImport: MavenInitialImportContext,
                       spec: MavenImportSpec
  ): MavenImportFinishedContext = withStructuredIdeActivity { activity ->

    val flow = MavenImportFlow()

    return@withStructuredIdeActivity runSync(spec) {

      @Suppress("HardCodedStringLiteral") console.addWarning("New Maven importing flow is enabled",
                                                             "New Maven importing flow is enabled, it is experimental feature. " + "\n\n" + "To revert to old importing flow, set \"maven.linear.import\" registry flag to false")

      currentContext = initialImport

      var readMavenFiles = doTask(MavenProjectBundle.message("maven.reading"), activity, MavenImportStats.ReadingTask::class.java) {
        currentContext?.indicator?.checkCanceled()
        flow.readMavenFiles(initialImport, indicator)
      }

      if (readMavenFiles.wrapperData != null) {
        try {
          readMavenFiles = flow.setupMavenWrapper(readMavenFiles)
          readMavenFiles.initialContext.generalSettings.mavenHome = MavenServerManager.WRAPPED_MAVEN
        }
        catch (e: Throwable) {
          MavenLog.LOG.warn(e)
        }
      }
      flow.updateProjectManager(readMavenFiles)

      val dependenciesContext = doTask(MavenProjectBundle.message("maven.resolving"), activity,
                                       MavenImportStats.ResolvingTask::class.java) {
        currentContext?.indicator?.checkCanceled()
        flow.resolveDependencies(readMavenFiles)
      }

      val resolvePlugins = doTask(MavenProjectBundle.message("maven.downloading.plugins"), activity,
                                  MavenImportStats.PluginsResolvingTask::class.java) {
        currentContext?.indicator?.checkCanceled()
        flow.resolvePlugins(dependenciesContext)
      }


      val importContext = doTask(MavenProjectBundle.message("maven.project.importing"), activity,
                                 MavenImportStats.ApplyingModelTask::class.java) {
        currentContext?.indicator?.checkCanceled()
        flow.commitToWorkspaceModel(dependenciesContext, activity)
      }

      return@runSync doTask(MavenProjectBundle.message("maven.post.processing"), activity,
                            MavenImportStats.ConfiguringProjectsTask::class.java) {
        currentContext?.indicator?.checkCanceled()
        flow.runPostImportTasks(importContext)
        runLegacyListeners(readMavenFiles) { projectImportCompleted() }
        setProjectSettings(initialImport)
        MavenResolveResultProblemProcessor.notifyMavenProblems(project) // remove this, should be in appropriate phase
        return@doTask MavenImportFinishedContext(importContext)
      }
    }.also {
      it.context?.let {
        project.messageBus
          .syncPublisher(MavenImportListener.TOPIC)
          .importFinished(it.resolvedContext.projectsToImport, it.modulesCreated)
      }
    }

  }

  private fun getWaitingPromise(): AsyncPromise<MavenImportFinishedContext> {
    if (ApplicationManager.getApplication().isDispatchThread) return waitingPromise
    val ref = Ref<AsyncPromise<MavenImportFinishedContext>>()
    ApplicationManager.getApplication().invokeAndWait {
      ref.set(waitingPromise)
    }
    return ref.get()
  }


  fun isImportingInProgress(): Boolean {
    val context = currentContext
    return context != null && context !is MavenImportFinishedContext
  }

  @RequiresEdt
  fun getImportFinishPromise(): Promise<MavenImportFinishedContext> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getWaitingPromise()
  }

  fun scheduleImportAll(spec: MavenImportSpec): MavenImportingResult {
    if (isRecursiveImportCalledFromMavenProjectsManagerWatcher()) {
      return MavenImportingResult(getWaitingPromise(), null, null)
    }
    ApplicationManager.getApplication().assertIsDispatchThread()
    val manager = MavenProjectsManager.getInstance(project)
    if (isImportingInProgress()) {
      return MavenImportingResult(getWaitingPromise(), null, null)
    }
    val settings = MavenWorkspaceSettingsComponent.getInstance(project)
    return openProjectAndImport(
      FilesList(manager.projectsTree.managedFilesPaths.mapNotNull { LocalFileSystem.getInstance().findFileByPath(it) }),
      settings.settings.getImportingSettings(),
      settings.settings.getGeneralSettings(), spec)
  }


  fun scheduleUpdate(filesToUpdate: List<VirtualFile>, filesToDelete: List<VirtualFile>, spec: MavenImportSpec): MavenImportingResult {
    if (isRecursiveImportCalledFromMavenProjectsManagerWatcher()) {
      return MavenImportingResult(getWaitingPromise(), null, null)
    }

    ApplicationManager.getApplication().assertIsDispatchThread()
    val manager = MavenProjectsManager.getInstance(project)
    val settings = MavenWorkspaceSettingsComponent.getInstance(project)
    manager.projectsTree.removeManagedFiles(filesToDelete)
    return openProjectAndImport(FilesList(filesToUpdate), settings.settings.getImportingSettings(),
                                settings.settings.getGeneralSettings(), spec)
  }

  private fun isRecursiveImportCalledFromMavenProjectsManagerWatcher(): Boolean {
    return isImportingInProgress()
  }

  // Action: Show statistic/events in Console

  private fun <Result> doTask(message: @BuildEventsNls.Message String,
                              parentActivity: StructuredIdeActivity,
                              activityKlass: Class<*>,
                              init: () -> Result): Result where Result : MavenImportContext = withStructuredIdeActivity(parentActivity,
                                                                                                                        activityKlass) {
    return@withStructuredIdeActivity console.runTask(message, init).also { ctx -> currentContext = ctx }
  }


  private fun runSync(spec: MavenImportSpec, init: () -> MavenImportFinishedContext): MavenImportFinishedContext {
    console.startImport(project.getService(SyncViewManager::class.java), spec)
    try {
      return init()
    }
    catch (e: Exception) {
      if (!project.isDisposed) {
        console.addException(e, project.getService(SyncViewManager::class.java))
      }
      else {
        MavenLog.LOG.warn(e)
      }

      return MavenImportFinishedContext(e, project)
    }
    finally {
      console.finishImport()
    }
  }

  fun forceStopImport() {
    currentContext?.indicator?.cancel()
  }


  private fun <T> withStructuredIdeActivity(f: (StructuredIdeActivity) -> T): T {
    val startedActivity = ProjectImportCollector.IMPORT_ACTIVITY.started(project, withData(MavenImportStats.ImportingTask::class.java))

    try {
      return f(startedActivity)
    }
    finally {
      startedActivity.finished()
    }
  }

  private fun <T> withStructuredIdeActivity(parent: StructuredIdeActivity, klass: Class<*>, f: (StructuredIdeActivity) -> T): T {
    val startedActivity = ProjectImportCollector.IMPORT_STAGE.startedWithParent(project, parent,
                                                                                { listOf(ProjectImportCollector.TASK_CLASS.with(klass)) })
    try {
      return f(startedActivity)
    }
    finally {
      startedActivity.finished()
    }

  }

  private fun withData(klass: Class<*>): () -> List<EventPair<*>> = {
    val data: MutableList<EventPair<*>> = mutableListOf(ExternalSystemActionsCollector.EXTERNAL_SYSTEM_ID.with(MavenUtil.MAVEN_NAME))
    if (mavenPluginInfo != null) {
      data.add(EventFields.PluginInfo.with(mavenPluginInfo))
    }
    data.add(ProjectImportCollector.TASK_CLASS.with(klass))
    data
  }

  private fun runUnderIndicator(title: @NlsContexts.ProgressTitle String, action: (MavenProgressIndicator) -> Unit) {
    executor.execute {
      ProgressManager.getInstance()
        .run(object : Task.Backgroundable(project, title) {
          override fun run(indicator: ProgressIndicator) {
            action(MavenProgressIndicator(project, indicator) { console })
          }
        })
    }
  }


  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenImportingManager {
      return project.getService(MavenImportingManager::class.java)
    }

    @JvmField
    @Topic.ProjectLevel
    val LEGACY_PROJECT_MANAGER_LISTENER = Topic.create("Maven Project Manager Listener bus", MavenProjectsManager.Listener::class.java)

  }
}