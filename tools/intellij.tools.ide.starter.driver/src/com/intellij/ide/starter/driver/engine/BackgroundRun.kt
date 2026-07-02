package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.syncVfs
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.nextRestartLaunchNameOf
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.process.collectJavaThreadDumpSuspendable
import com.intellij.ide.starter.report.DetailsOnCI
import com.intellij.ide.starter.testContextCache.TestContextCache
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.catchAll
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class BackgroundRun(
  val startResult: Deferred<IDEStartResult>,
  private val driverWithoutAwaitedConnection: Driver,
  val process: IDEHandle,
  internal val runContext: IDERunContext,
) {

  /**
   * Starts a replacement of this run under the given launch name. The function that launched this run sets it,
   * because only that function knows the whole launch recipe. See [restart].
   */
  @ApiStatus.Internal
  var restarter: ((launchName: String) -> BackgroundRun)? = null

  internal open val ownedProcesses: List<IDEHandle> get() = listOf(process)

  val driver: Driver by lazy {
    if (!driverWithoutAwaitedConnection.isConnected) {
      runCatching {
        waitFor("Driver is connected", 3.minutes) {
          if (!process.isAlive) {
            throwIdeStartFailure(startResult, process.id)
          }
          driverWithoutAwaitedConnection.isConnected
        }
      }.onFailure { t ->
        catchAll("Close the IDE that the Driver did not reach") { driverWithoutAwaitedConnection.closeIdeAndWait(1.minutes) }
        throw t
      }
    }
    CurrentTestLogSynchronizer(driverWithoutAwaitedConnection, runContext).start()
    driverWithoutAwaitedConnection
  }

  /**
   * Alias for [useDriverAndCloseIde] to make it possible apply `fun test() = bgRun.test { }` syntax in tests.
   */
  fun <R> test(
    closeIdeTimeout: Duration = 1.minutes,
    takeScreenshot: Boolean = true,
    shutdownHook: Driver.() -> Unit = {},
    block: Driver.() -> R,
  ) {
    useDriverAndCloseIde(closeIdeTimeout, takeScreenshot, shutdownHook, block)
  }

  open fun <R> useDriverAndCloseIde(
    closeIdeTimeout: Duration = 1.minutes,
    takeScreenshot: Boolean = true,
    shutdownHook: Driver.() -> Unit = {},
    block: Driver.() -> R,
  ): IDEStartResult {
    val testError = runCatching {
      syncVfsAfterProjectFilesRestore()
      driver.withContext { block(this) }
    }.exceptionOrNull()
    catchAll { shutdownHook(driver) }
    val closeResult = runCatching { driver.closeIdeAndWait(closeIdeTimeout, takeScreenshot) }
    if (testError == null) return closeResult.getOrThrow()
    closeResult.exceptionOrNull()?.let(testError::addSuppressed)
    throw testError
  }

  /**
   * Same as [useDriverAndCloseIde] but waits for the IDE to close itself after the run.
   *
   * The IDE is closed on any exception, or if it doesn't close automatically after the block execution completes.
   */
  open fun <R> useDriverAndWaitIdeIsClosed(closeIdeTimeout: Duration = 1.minutes, block: Driver.() -> R): IDEStartResult {
    lateinit var ideStartResult: IDEStartResult
    runCatching {
      syncVfsAfterProjectFilesRestore()
      driver.withContext {
        block(this)
        if (isConnected) takeScreenshot("beforeIdeClosed")
      }
    }.onFailure { e ->
      runCatching {
        driver.exitApplication()
        waitFor(timeout = closeIdeTimeout,
                errorMessage = { "Error on exit application via Driver" },
                condition = { !process.isAlive })
      }.exceptionOrNull()?.let { t ->
        logError("Error on exit application via Driver", t)
        forceKill()
        e.addSuppressed(t)
      }
      catchAll { driver.close() }
      throw e
    }.onSuccess {
      ideStartResult = driver.waitToClose(closeIdeTimeout)
    }
    return ideStartResult
  }

  open fun closeIdeAndWait(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true) {
    driver.closeIdeAndWait(closeIdeTimeout, takeScreenshot)
  }

  /**
   * Closes this run with [close], and starts a replacement from the same launch recipe.
   *
   * The replacement takes [launchName], or else [nextRestartLaunchNameOf] the current one. The default [close] waits
   * for the result, so a failed run fails the restart. In split mode, it closes the frontend and the backend.
   */
  fun restart(
    launchName: String? = null,
    close: BackgroundRun.() -> Unit = { closeIdeAndWait() },
  ): BackgroundRun {
    val restarter = restarter
                    ?: error("This ${this::class.simpleName} cannot restart. Only a run that runIdeWithDriver started can restart.")
    close()
    return restarter(launchName ?: nextRestartLaunchNameOf(runContext.launchName))
  }

  @ApiStatus.Internal
  suspend fun collectJavaThreadDump(artifactFileName: String) {
    val ideReportingData = runContext.lastIdeReportingData
    collectJavaThreadDumpSuspendable(runContext.testContext.ide.resolveAndDownloadTheSameJDKOrFallback(),
                                     ideReportingData.logsDir,
                                     process.id.toLong(),
                                     ideReportingData.logsDir.resolve(artifactFileName))
  }

  protected fun Driver.closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean = true): IDEStartResult {
    val logPrefix = "[Closing ${process.id}]"
    val connected = runCatching { isConnected }.getOrDefault(false)
    val exitError = if (connected) {
      if (takeScreenshot) {
        catchAll("Take a screenshot before the IDE closes") { takeScreenshot("beforeIdeClosed") }
      }
      runCatching {
        exitApplication()
        waitFor(message = "$logPrefix The IDE process ended",
                timeout = closeIdeTimeout,
                errorMessage = { "$logPrefix The IDE did not stop after the Driver asked it to exit. ${ideStateDetails()}" },
                condition = { !process.isAlive })
      }.exceptionOrNull()?.let { t ->
        logError("$logPrefix Error on exit application via Driver", t)
        t.takeIf { process.isAlive }?.also { forceKill() }
      }
    }
    else if (process.isAlive) {
      val message = "$logPrefix The Driver has no connection, and the IDE is still alive. ${ideStateDetails()}"
      logError(message)
      forceKill()
      IllegalStateException(message)
    }
    else {
      logOutput("$logPrefix The IDE stopped before the Driver asked it to exit")
      null
    }

    catchAll { if (isConnected) close() }
    return awaitProcessEnd(logPrefix, closeIdeTimeout, exitError)
  }

  protected fun Driver.waitToClose(closeIdeTimeout: Duration): IDEStartResult {
    val logPrefix = "[Waiting shutdown ${process.id}]"
    val exitError = runCatching {
      waitFor(message = "$logPrefix The IDE process ended",
              timeout = closeIdeTimeout,
              errorMessage = { "$logPrefix The IDE did not stop by itself. ${ideStateDetails()}" },
              condition = { !process.isAlive })
    }.exceptionOrNull()?.also { e ->
      logError("$logPrefix Error on waiting for application exit", e)
      catchAll("Take a screenshot before the IDE is killed") { takeScreenshot("beforeIdeKilled") }
      forceKill()
    }
    return awaitProcessEnd(logPrefix, closeIdeTimeout, exitError)
  }

  /**
   * Waits for the IDE process to end, and gives the result of the run.
   *
   * [exitError] is the failure of a close that ended in a [forceKill]. The run of a killed IDE reports only that
   * kill, so the close error is the real one and wins over the result. A close that needed no kill gives `null`.
   */
  private fun Driver.awaitProcessEnd(logPrefix: String, closeIdeTimeout: Duration, exitError: Throwable?): IDEStartResult {
    runCatching {
      waitFor("$logPrefix Process is closed", closeIdeTimeout) { !process.isAlive }
    }.onFailure { e ->
      logError("$logPrefix Error waiting IDE is closed", e)
      forceKill()
      val processError = IllegalStateException("$logPrefix Process didn't die after waiting for Driver to close IDE", e)
      throw exitError?.also { it.addSuppressed(processError) } ?: processError
    }

    @Suppress("TestOnlyProblems")
    val result = runCatching { timeoutRunBlocking(5.minutes) { startResult.await() } }
    if (exitError == null) return result.getOrThrow()
    result.exceptionOrNull()?.let(exitError::addSuppressed)
    throw exitError
  }

  private fun ideStateDetails(): String = runCatching {
    @Suppress("TestOnlyProblems")
    val connected = runCatching {
      timeoutRunBlocking(10.seconds) { withContext(Dispatchers.IO) { driverWithoutAwaitedConnection.isConnected } }
    }.fold({ it.toString() }, { "unknown" })
    val artifacts = DetailsOnCI.instance.getLinkToCIArtifacts(runContext.lastIdeReportingData)
    "State: product=${runContext.testContext.testCase.ideInfo.fullName}, " +
    "commandLine=${runContext.commandLine(runContext).args.joinToString(" ")}, " +
    "driverConnected=$connected, processAlive=${process.isAlive}, " +
    "processId=${process.id}" + (artifacts?.let { ", artifacts=$it" } ?: "")
  }.getOrElse { "State: not known, ${it.message}" }

  open fun forceKill() {
    catchAll("Restrict IDE errors to existing before force kill") {
      runContext.lastIdeReportingData.restrictIdeErrorReportsToExistingFiles()
    }
    logOutput("[Closing ${process.id}] Performing force kill")
    process.kill()
  }
}

/**
 * Refreshes the VFS when a restore of [com.intellij.ide.starter.testContextCache.CachedProjectConfiguration.createTestContext] still owes one, and takes that need.
 *
 * `ProjectFilesRestorer` rewinds the project directory with plain file-system calls, so the IDE does not see them.
 * The restore runs before the test starts an IDE, so the cache entry carries the need across.
 * The first caller with a live IDE discharges it.
 * A reusable session holds its IDE, so it calls [syncVfsWithRestoredProjectFiles] itself after its restore.
 *
 * Only a restore that wrote to the project owes a refresh. A context over no cached project owes nothing, and
 * neither does the baseline capture of the first test over one, which only reads the project files.
 *
 * A test that changes a file outside the IDE still calls [syncVfs] itself. No restore reports such a change.
 * See https://youtrack.jetbrains.com/issue/AT-4588.
 */
internal fun BackgroundRun.syncVfsAfterProjectFilesRestore() {
  if (TestContextCache.consumeProjectFilesVfsRefresh(runContext.testContext)) {
    syncVfsWithRestoredProjectFiles()
  }
}

/** Refreshes the VFS after a restore that wrote to the project files. */
internal fun BackgroundRun.syncVfsWithRestoredProjectFiles() {
  step("Sync the VFS with the restored project files") {
    driver.syncVfs()
  }
}

internal fun throwIdeStartFailure(startResult: Deferred<IDEStartResult>, processId: String): Nothing {
  // Need to wait for startResult as it carries JVM startup failures with captured stderr, so propagate it rather than reporting a generic Driver connection error.
  @Suppress("TestOnlyProblems")
  val result = timeoutRunBlocking(5.minutes) {
    startResult.await()
  }
  val message = "Couldn't wait for the driver to connect: IDE process pid[$processId] exited with code ${result.exitCode ?: "<unknown>"}"
  logError(message)
  throw IllegalStateException(message)
}
