package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.saveAllDocuments
import com.intellij.driver.sdk.saveSettings
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.IdeEventQueue
import com.intellij.driver.sdk.ui.ProjectUtil
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Waits until the project at [projectPath] is open, whatever else is open beside it.
 *
 * The match has to be unambiguous, not the count: a baseline may hold several projects, and [reopenProjects]
 * awaits each in turn while the earlier ones are already open.
 */
internal fun Driver.waitForOpenedProject(projectPath: String, rdTarget: RdTarget, timeout: Duration = PROJECT_OPEN_TIMEOUT): Project {
  return waitForOne(
    message = "Configured project is open: $projectPath",
    timeout = timeout,
    getter = { getOpenProjects(rdTarget) },
    checker = { project -> matchesConfiguredProject(project, projectPath, rdTarget) },
    errorMessage = { "Actual opened projects are: ${it.joinToString(", ")}" },
  )
}

private fun Driver.matchesConfiguredProject(project: Project, projectPath: String, rdTarget: RdTarget): Boolean {
  return if (isRemDevMode && rdTarget == RdTarget.FRONTEND) {
    // The frontend holds no backend base path, so match the name that the backend gives the project at that path.
    // The name can differ from the directory name, for example through `.idea/.name`.
    project.getName() == getOpenProjects(RdTarget.BACKEND).singleOrNull { isSamePath(it.getBasePath(), projectPath) }?.getName()
  }
  else {
    isSamePath(project.getBasePath(), projectPath)
  }
}

/** [Project.getBasePath] uses `/` also on Windows, so compare the paths, not the strings. */
private fun isSamePath(basePath: String?, projectPath: String): Boolean =
  basePath != null && Path.of(basePath) == Path.of(projectPath)

internal fun Driver.reopenProjects(projectPaths: List<String>) {
  if (projectPaths.isEmpty()) {
    return
  }
  step("Reopen projects: ${projectPaths.joinToString(", ")}") {
    projectPaths.forEach { projectPath -> openAndWaitForProject(projectPath) }
  }
}

internal fun Driver.openAndWaitForProject(projectPath: String) {
  withContext(OnDispatcher.DEFAULT) {
    utility<ProjectUtil>(RdTarget.BACKEND).openOrImport(projectPath, null, forceOpenInNewFrame = true)
  } ?: error("Failed to open project: $projectPath")
  if (isRemDevMode) waitForOpenedProject(projectPath, RdTarget.FRONTEND)
  else waitForOpenedProject(projectPath, RdTarget.DEFAULT)
}

internal fun Driver.saveAndStabilizeStateForReuse() {
  step("Save and stabilize reused IDE state") {
    saveAllDocuments()
    flushIdeEventQueue()
    waitForActionsOnSave()
    // Save settings only persists RecentProjectsManager state, so sync currently open projects before snapshotting it.
    updateLastProjectPath()
    saveSettings(forceSavingAllSettings = true, rdTargets = settingsReloadTargets())
    // Saving documents and Actions on Save can enqueue indexing after the project already looked idle.
    waitForProjectsWithNoPendingIndexingWork("reading reused IDE state")
  }
}

internal fun Driver.closeProjects() {
  val projects = getOpenProjects(RdTarget.BACKEND)
  val projectManager = utility<ProjectManagerEx>().getInstanceEx()
  withContext(OnDispatcher.EDT, LockSemantics.READ_ACTION) {
    projects.forEach { project ->
      val basePath = project.getBasePath()
      if (!projectManager.forceCloseProject(project, save = false)) {
        error("Failed to close project left by test: $basePath")
      }
    }
  }
  if (isRemDevMode) {
    waitForClosedFrontendProjects()
  }
}

/**
 * Waits until the frontend holds no open project.
 *
 * The frontend close is asynchronous, so it can outlive [closeProjects], and a project that disposes later
 * breaks the next recovery. [reopenProjects] cannot cover this: it matches by name, so it never sees such a
 * project.
 */
private fun Driver.waitForClosedFrontendProjects() {
  waitFor(
    message = "The frontend closed every project",
    timeout = PROJECT_CLOSE_TIMEOUT,
    getter = { getOpenProjects(RdTarget.FRONTEND) },
    checker = { frontendProjects -> frontendProjects.isEmpty() },
    errorMessage = { "The frontend still holds these projects: ${it.joinToString(", ")}" },
  )
}

private fun Driver.flushIdeEventQueue() {
  withContext(OnDispatcher.EDT) {
    utility<IdeEventQueue>().getInstance().flushQueue()
  }
}

private fun Driver.waitForActionsOnSave() {
  val projects = getOpenProjects(RdTarget.BACKEND)
  if (projects.isEmpty()) {
    logOutput("[ReusedRecovery] No open projects; skipping Actions on Save wait")
    return
  }

  logOutput("[ReusedRecovery] Waiting for Actions on Save to finish: ${projects.joinToString(", ")}")
  projects.forEach { project ->
    utility<ActionsOnSaveManager>().getInstance(project).waitForTasks()
  }
}

internal fun Driver.waitForProjectsInitialized(projects: Array<Project>) {
  if (projects.isEmpty()) {
    logOutput("[ReusedRecovery] No open projects; skipping project initialization wait")
    return
  }

  logOutput("[ReusedRecovery] Waiting for open projects to initialize: ${projects.contentToString()}")
  projects.forEach { project ->
    waitForProjectInitialized(project)
  }
}

private fun Driver.waitForProjectInitialized(project: Project) {
  val basePath = project.getBasePath()
  waitFor(
    message = "Project is initialized: $basePath",
    timeout = INDEXES_TIMEOUT,
    errorMessage = { "Project is not initialized: $basePath" },
  ) {
    runCatching { project.isOpen() && project.isInitialized() }.getOrDefault(false)
  }
}

/**
 * Waits until [project] has applied its project model files.
 *
 * A project that opens from the workspace model cache loads its `iml` and `xml` files later, in the background. That
 * load replaces a change that the test makes before it ends.
 */
private fun Driver.waitForProjectModelFilesLoaded(project: Project) {
  val basePath = project.getBasePath()
  waitFor(
    message = "Project model files are loaded: $basePath",
    timeout = INDEXES_TIMEOUT,
    errorMessage = { "Project model files are not loaded: $basePath" },
  ) {
    utility<JpsProjectLoadingManager>().getInstance(project).isProjectLoaded()
  }
}

/**
 * Waits until each global workspace model has applied its `jdk.table.xml` and global library files.
 *
 * An IDE that starts from the global workspace model cache loads those files 5 s later, in the background. That
 * load replaces the SDKs that a test adds before it.
 */
private fun Driver.waitForGlobalModelFilesLoaded() {
  waitFor(
    message = "Global model files are loaded",
    timeout = INDEXES_TIMEOUT,
    errorMessage = { "Global model files are not loaded" },
  ) {
    service<GlobalWorkspaceModelRegistry>(RdTarget.BACKEND).getGlobalModels().all { it.getJpsSyncJob().isCompleted() }
  }
}

internal fun Driver.waitForProjectsWithNoPendingIndexingWork(reason: String) {
  waitForGlobalModelFilesLoaded()
  val projects = getOpenProjects(RdTarget.BACKEND)
  if (projects.isEmpty()) {
    logOutput("[ReusedRecovery] No open projects; skipping pending indexing work wait before $reason")
    return
  }

  logOutput("[ReusedRecovery] Waiting for open projects to have no pending indexing work before $reason: ${projects.joinToString(", ")}")
  projects.forEach { project ->
    waitForProjectInitialized(project)
    waitForProjectModelFilesLoaded(project)
    val basePath = project.getBasePath()
    val dumbService = utility<DumbServiceImpl>().getInstance(project)
    // Smart mode can be observed before required startup dumb tasks are submitted, so force submission before inspecting queues.
    dumbService.ensureInitialDumbTaskRequiredForSmartModeSubmitted()
    val scanner = utility<UnindexedFilesScannerExecutorImpl>().getInstance(project)
    val smartModeScheduler = service<SmartModeScheduler>(project)
    var lastReadiness = "not checked"
    waitFor(
      message = "Project has no pending indexing work before $reason: $basePath",
      timeout = INDEXES_TIMEOUT,
      errorMessage = { "Project still has indexing work before $reason: $basePath. Last state: $lastReadiness" },
    ) {
      if (!project.isOpen()) {
        lastReadiness = "projectOpen=false"
        return@waitFor false
      }

      val scannerQueued = scanner.getHasQueuedTasks()
      val dumbScheduled = dumbService.hasScheduledTasks()
      val dumbRunning = dumbService.isRunning()
      // SmartModeScheduler folds scanner-running and dumb-state signals; this catches gaps left by a plain DumbService.isDumb check.
      val smartModeSchedulerMode = smartModeScheduler.getCurrentMode()
      lastReadiness = "scannerQueued=$scannerQueued, dumbScheduled=$dumbScheduled, dumbRunning=$dumbRunning, " +
                      "smartModeSchedulerMode=$smartModeSchedulerMode"
      !scannerQueued && !dumbScheduled && !dumbRunning && smartModeSchedulerMode == 0
    }
  }
}

private fun Driver.updateLastProjectPath() {
  logOutput("[ReusedRecovery] Updating RecentProjectsManager last project path")
  utility<RecentProjectsManagerBase>().getInstanceEx().updateLastProjectPath()
}

@Remote("com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModelRegistry", rdTarget = RdTarget.BACKEND)
private interface GlobalWorkspaceModelRegistry {
  fun getGlobalModels(): List<GlobalWorkspaceModel>
}

@Remote("com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel", rdTarget = RdTarget.BACKEND)
private interface GlobalWorkspaceModel {
  fun getJpsSyncJob(): Job
}

@Remote("kotlinx.coroutines.Job", rdTarget = RdTarget.BACKEND)
private interface Job {
  fun isCompleted(): Boolean
}

@Remote("com.intellij.workspaceModel.ide.JpsProjectLoadingManager", rdTarget = RdTarget.BACKEND)
private interface JpsProjectLoadingManager {
  fun getInstance(project: Project): JpsProjectLoadingManagerImpl
}

@Remote("com.intellij.workspaceModel.ide.impl.JpsProjectLoadingManagerImpl", rdTarget = RdTarget.BACKEND)
private interface JpsProjectLoadingManagerImpl {
  fun isProjectLoaded(): Boolean
}

@Remote("com.intellij.openapi.project.ex.ProjectManagerEx", rdTarget = RdTarget.BACKEND)
private interface ProjectManagerEx {
  fun getInstanceEx(): ProjectManagerEx
  fun forceCloseProject(project: Project, save: Boolean): Boolean
}

@Remote("com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager", rdTarget = RdTarget.BACKEND)
private interface ActionsOnSaveManager {
  fun getInstance(project: Project): ActionsOnSaveManager
  fun waitForTasks()
}

@Remote("com.intellij.openapi.project.DumbServiceImpl", rdTarget = RdTarget.BACKEND)
private interface DumbServiceImpl {
  fun getInstance(project: Project): DumbServiceImpl
  fun ensureInitialDumbTaskRequiredForSmartModeSubmitted()
  fun hasScheduledTasks(): Boolean
  fun isRunning(): Boolean
}

@Remote("com.intellij.openapi.project.SmartModeScheduler", rdTarget = RdTarget.BACKEND)
private interface SmartModeScheduler {
  fun getCurrentMode(): Int
}

@Remote("com.intellij.util.indexing.UnindexedFilesScannerExecutorImpl", rdTarget = RdTarget.BACKEND)
private interface UnindexedFilesScannerExecutorImpl {
  fun getInstance(project: Project): UnindexedFilesScannerExecutorImpl
  fun getHasQueuedTasks(): Boolean
}

@Remote("com.intellij.ide.RecentProjectsManagerBase", rdTarget = RdTarget.BACKEND)
private interface RecentProjectsManagerBase {
  fun getInstanceEx(): RecentProjectsManagerBase
  fun updateLastProjectPath()
}

private val INDEXES_TIMEOUT = 5.minutes
private val PROJECT_OPEN_TIMEOUT = 30.seconds
/** A fresh IDE also starts up, and a split frontend must connect to the backend before it holds the project. */
internal val INITIAL_PROJECT_OPEN_TIMEOUT = 2.minutes
// The frontend close saves the project and can run a modal progress, so it needs more time than the open wait.
private val PROJECT_CLOSE_TIMEOUT = 1.minutes
