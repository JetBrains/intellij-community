package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.reloadChangedSettings
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.withoutPauseOnIndicators
import com.intellij.ide.starter.driver.driver.remoteDev.RemoteDevBackgroundRun
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.syncVfsWithRestoredProjectFiles
import com.intellij.ide.starter.driver.reused.ReusedSessionStrategy
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Restores the IDE of one reused session between tests, and restarts it when it cannot be restored in place.
 *
 * There is no warm-up launch: the first start is the one the tests run on, and its recovery baseline is captured
 * from that same process once its project has opened and indexed.
 */
internal class IdeStateRecovery(
  private val strategy: ReusedSessionStrategy,
  private val extensionContext: ExtensionContext,
  private val testContext: IDETestContext,
  private val runTimeout: Duration,
  private val baseline: ReusedSessionBaseline,
) {
  /** Every IDE run of this recovery that can still have a live process, newest last. */
  private val startedRuns: MutableList<BackgroundRun> = CopyOnWriteArrayList()

  /** Starts the reused IDE and captures its saved, stable baseline on the first call. */
  fun startIde(): BackgroundRun = startFromBaseline { strategy.startIde(extensionContext, testContext, runTimeout) }

  /** Stops [bgRun], as the strategy of this session decides an IDE of it may be stopped. */
  fun closeIde(bgRun: BackgroundRun) {
    withoutPauseOnIndicators { strategy.closeIde(extensionContext, bgRun) }
  }

  /**
   * Restores the IDE for the next test, and answers with the IDE the test runs on.
   *
   * A live IDE is restored in place, see [recoverLiveIde]. An IDE that stopped during the previous test restarts at once.
   */
  fun recoverForNextTest(bgRun: BackgroundRun): BackgroundRun {
    // An earlier run matters only while a process of it lives, for example one whose close failed.
    startedRuns.retainAll { run -> run !== bgRun && run.hasAliveProcess() }
    startedRuns.add(bgRun)
    return if (bgRun.hasAliveProcess()) recoverLiveIde(bgRun) else restartStoppedIde(bgRun)
  }

  /**
   * Restores [bgRun] in place on a worker within [LIVE_RECOVERY_TIMEOUT].
   *
   * It restarts the IDE when a changed setting cannot reload, and force-kills and restarts it when the restore fails.
   */
  private fun recoverLiveIde(bgRun: BackgroundRun): BackgroundRun {
    val threadName = "Reused IDE live recovery for ${bgRun.formatForRecoveryLog()}"
    val worker = newDaemonExecutor(threadName)
    val restore = runCatching {
      worker.submit<SettingsRecoveryPlan> { withoutPauseOnIndicators { restoreInPlace(bgRun) } }.getOrRethrowCause(LIVE_RECOVERY_TIMEOUT, threadName)
    }
    worker.shutdown()
    return restore.fold(
      onSuccess = { plan -> if (plan.requiresRestart) restartForSettings(bgRun, plan) else bgRun },
      onFailure = { failure -> restartAfterFailure(bgRun, worker, failure) },
    )
  }

  /**
   * Restores [bgRun] in place for the next test. It runs on the worker, and it never starts or closes an IDE.
   *
   * @return the settings plan of the previous test. With [SettingsRecoveryPlan.requiresRestart], it leaves the IDE as
   * it is for the restart. Otherwise the IDE is restored.
   */
  private fun restoreInPlace(bgRun: BackgroundRun): SettingsRecoveryPlan {
    bgRun.driver.checkResponsiveForRecovery()
    bgRun.driver.closeWindowsLeftByPreviousTest(baseline.visibleWindows)

    bgRun.driver.saveAndStabilizeStateForReuse()

    val settingsRecoveryPlan = baseline.settingsRecoveryPlan(testContext)
    if (settingsRecoveryPlan.requiresRestart) {
      return settingsRecoveryPlan
    }

    bgRun.driver.closeProjects()

    if (baseline.restore(testContext)) {
      bgRun.syncVfsWithRestoredProjectFiles()
    }
    val restoredRecentProjectsSettingsExists = testContext.recentProjectsSettingsFileExists()
    bgRun.driver.reloadChangedSettings(
      changedFileSpecs = settingsRecoveryPlan.changedFileSpecsWithRecentProjects(restoredRecentProjectsSettingsExists),
      deletedFileSpecs = settingsRecoveryPlan.deletedFileSpecsWithRecentProjects(restoredRecentProjectsSettingsExists),
      rdTargets = bgRun.driver.settingsReloadTargets(),
    )

    bgRun.driver.reopenProjects(baseline.openedProjectPaths)
    bgRun.driver.waitForProjectsWithNoPendingIndexingWork("starting the next reused IDE test")
    bgRun.waitForIdeFrameIfNeeded()
    bgRun.driver.checkNoUnexpectedWindows(baseline.visibleWindows)
    return settingsRecoveryPlan
  }

  /** Restarts [bgRun], which stopped during the previous test, from the baseline. Nothing is left to restore in place. */
  private fun restartStoppedIde(bgRun: BackgroundRun): BackgroundRun {
    val bgRunLabel = bgRun.formatForRecoveryLog()
    logError("[ReusedRecovery] The IDE stopped during the previous test: $bgRunLabel; restarting from the initial state")
    // An earlier attempt can leave a live run.
    forceKillEveryRun(worker = null, failure = null)
    return restartFromBaseline(
      bgRun,
      reason = "The IDE stopped during the previous test: $bgRunLabel. Recovery restarts it from the initial state",
      details = bgRunLabel,
    )
  }

  /** Closes [bgRun] and restarts it from the baseline, because a setting that the previous test changed cannot reload. */
  private fun restartForSettings(bgRun: BackgroundRun, settingsRecoveryPlan: SettingsRecoveryPlan): BackgroundRun {
    val planText = settingsRecoveryPlan.formatForRecoveryLog()
    logOutput("[ReusedRecovery] Settings require restart after project reopen: $planText")
    return runCatching { closeIde(bgRun) }.fold(
      onSuccess = { restartFromBaseline(bgRun, reason = "Settings require restart after project reopen: $planText", details = planText) },
      onFailure = { failure -> restartAfterFailure(bgRun, worker = null, failure = failure) },
    )
  }

  /** Force-kills [bgRun] and the [worker] that drives it, and restarts the IDE from the baseline after [failure]. */
  private fun restartAfterFailure(bgRun: BackgroundRun, worker: ExecutorService?, failure: Throwable): BackgroundRun {
    // Read before the kill, so the label reports the state the IDE had when recovery failed.
    val bgRunLabel = bgRun.formatForRecoveryLog()
    logError("[ReusedRecovery] Failed to recover responsive IDE in place for $bgRunLabel; restarting from the initial state", failure)
    forceKillEveryRun(worker, failure)
    return restartFromBaseline(
      bgRun,
      reason = "Responsive IDE recovery failed for $bgRunLabel. Recovery force-killed that IDE and restarts from the initial state",
      details = failure.stackTraceToString(),
    )
  }

  /**
   * Force-kills every live run of [startedRuns], waits until all of them are dead, and then closes each of them.
   *
   * With [worker], this also stops the recovery worker. The kill comes before the join, because a driver call ignores
   * the interrupt and returns only when its IDE dies.
   *
   * Fails when a run survives or the worker does not finish. Then something live can still use the shared system
   * directory, and a replacement IDE must not start on it.
   *
   * The close only waits until the launch of a run finishes. Every process is dead, so it can fail only with the exit
   * code of that death.
   */
  private fun forceKillEveryRun(worker: ExecutorService?, failure: Throwable?) {
    // Frees a worker parked in an interruptible wait. A worker inside a driver call ignores it, hence the kill.
    worker?.shutdownNow()
    startedRuns.filter { it.hasAliveProcess() }.forEach { run ->
      logOutput("[ReusedRecovery] Force killing IDE of the reused session: ${run.formatForRecoveryLog()}")
      runCatching { run.forceKill() }
        .onFailure { logError("[ReusedRecovery] Failed to force kill ${run.formatForRecoveryLog()}", it) }
    }
    val workerFinished = worker?.awaitTermination(RECOVERY_WORKER_JOIN_GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS) ?: true
    val stillAlive = startedRuns.filterNot { runCatching { awaitEveryProcessDead(it) }.isSuccess }
    if (workerFinished && stillAlive.isEmpty()) {
      startedRuns.forEach { run ->
        runCatching { closeIde(run) }
          .onFailure { logOutput("[ReusedRecovery] The stopped run ended with: ${it.message}") }
      }
      return
    }
    val reasons = buildList {
      if (!workerFinished) {
        add("its worker thread is still running $RECOVERY_WORKER_JOIN_GRACE after its IDE was force-killed")
      }
      if (stillAlive.isNotEmpty()) {
        add("${stillAlive.size} IDE run(s) survived a force kill: ${stillAlive.joinToString { it.formatForRecoveryLog() }}")
      }
    }
    throw IllegalStateException(
      "Reused IDE recovery cannot start a replacement IDE: ${reasons.joinToString("; ")}. " +
      "Two IDEs must not use one shared system directory.",
      failure,
    )
  }

  /**
   * Starts a replacement of [failedRun] from the baseline, and reports the restart.
   *
   * The caller has closed [failedRun], so no IDE of the session writes its files while the baseline is restored.
   */
  private fun restartFromBaseline(failedRun: BackgroundRun, reason: String, details: String): BackgroundRun {
    // The old IDE is closed, so the restart closes nothing.
    val restartedRun = startFromBaseline { failedRun.restart(close = {}) }
    reportRecoveryRestartRequired(
      reason = reason,
      details = details,
      previousArtifacts = failedRun.runContext.lastIdeReportingData,
      newArtifacts = restartedRun.runContext.lastIdeReportingData,
    )
    return restartedRun
  }

  /**
   * Restores the baseline and records the new run before preparation can block.
   * The first start restores the prepared project files and captures the baseline after it. A failed preparation or
   * capture closes the run.
   */
  private fun startFromBaseline(start: () -> BackgroundRun): BackgroundRun = withoutPauseOnIndicators {
    val hasBaseline = baseline.isCaptured
    val projectFilesRestored = baseline.restoreBeforeStart(testContext)
    val bgRun = start()
    startedRuns.add(bgRun)
    try {
      waitUntilReadyForTest(bgRun)
      bgRun.driver.waitForStartupWorkFinished()
      if (projectFilesRestored) {
        bgRun.syncVfsWithRestoredProjectFiles()
      }
      if (!hasBaseline) {
        captureBaseline(bgRun)
      }
      else {
        bgRun.driver.waitForProjectsWithNoPendingIndexingWork("starting the first test on the reused IDE")
      }
      bgRun
    }
    catch (failure: Throwable) {
      runCatching { closeIde(bgRun) }
        .onFailure(failure::addSuppressed)
      throw failure
    }
  }

  private fun waitUntilReadyForTest(bgRun: BackgroundRun) {
    val projects = getOrOpenInitialProjects(bgRun)
    bgRun.waitForIdeFrameIfNeeded()
    bgRun.driver.waitForProjectsInitialized(projects)
  }

  private fun captureBaseline(bgRun: BackgroundRun) {
    bgRun.driver.waitForProjectsWithNoPendingIndexingWork("capturing live recovery baseline")
    bgRun.driver.saveAndStabilizeStateForReuse()
    baseline.capture(
      testContext,
      bgRun.driver.getOpenProjects(RdTarget.BACKEND).map { project -> project.getBasePath() },
      bgRun.driver.visibleNonTransientWindowsData(),
    )
  }

  private fun getOrOpenInitialProjects(bgRun: BackgroundRun): Array<Project> {
    if (testContext.testCase.projectInfo == NoProject) {
      return emptyArray()
    }
    openInitialProjectIfNeeded(bgRun)

    return bgRun.driver.step("Ensure configured project is open") {
      val projectPath = testContext.resolvedProjectHome.toAbsolutePath().toString()
      if (bgRun is RemoteDevBackgroundRun) {
        bgRun.driver.waitForOpenedProject(projectPath, RdTarget.FRONTEND, INITIAL_PROJECT_OPEN_TIMEOUT)
      }
      arrayOf(bgRun.driver.waitForOpenedProject(projectPath, RdTarget.BACKEND, INITIAL_PROJECT_OPEN_TIMEOUT))
    }
  }

  private fun openInitialProjectIfNeeded(bgRun: BackgroundRun) {
    if (bgRun !is RemoteDevBackgroundRun && bgRun.runContext.testContext.ide.vmOptions.hasHeadlessMode()) {
      val projectPath = testContext.resolvedProjectHome.toAbsolutePath().toString()
      logOutput(
        "[ReusedRecovery] Opening configured project via driver in headless monolith run: $projectPath. " +
        "See https://youtrack.jetbrains.com/issue/IJPL-221229"
      )
      bgRun.driver.openAndWaitForProject(projectPath)
    }
  }
}

/**
 * Runs [block] on a dedicated thread and waits up to [timeout] for its result.
 *
 * Nothing is cancelled on timeout: a driver call ignores [Thread.interrupt] and unwinds only once its IDE goes
 * away. Use this only for a probe that owns nothing a caller would have to clean up.
 */
internal fun <T> callWithTimeout(timeout: Duration, threadName: String, block: () -> T): T {
  val executor = newDaemonExecutor(threadName)
  try {
    return executor.submit(block).getOrRethrowCause(timeout, threadName)
  }
  finally {
    executor.shutdown()
  }
}

private fun BackgroundRun.hasAliveProcess(): Boolean = ownedProcesses.any { it.isAlive }

/** One daemon thread called [threadName], so a call stuck in the IDE is identifiable in a thread dump. */
private fun newDaemonExecutor(threadName: String): ExecutorService =
  Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName).apply { isDaemon = true } }

/** Waits up to [timeout], naming [threadName] on timeout and unwrapping an [ExecutionException] into its real cause. */
private fun <T> Future<T>.getOrRethrowCause(timeout: Duration, threadName: String): T {
  try {
    return get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }
  catch (e: TimeoutException) {
    throw IllegalStateException("$threadName did not complete within $timeout", e)
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e
  }
}

// Above the sum of live recovery's own bounded waits, so this only fires for a step that has no timeout.
private val LIVE_RECOVERY_TIMEOUT = 15.minutes

// Enough for a worker to unwind a failed driver call; the process dying is what unblocks it, not the interrupt.
private val RECOVERY_WORKER_JOIN_GRACE = 1.minutes
