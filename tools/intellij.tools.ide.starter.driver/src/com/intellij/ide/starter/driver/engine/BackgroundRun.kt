package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.utils.catchAll
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class BackgroundRun(val startResult: Deferred<IDEStartResult>, driverWithoutAwaitedConnection: Driver, val process: IDEHandle) {

  val driver: Driver by lazy {
    if (!driverWithoutAwaitedConnection.isConnected) {
      runCatching {
        waitFor("Driver is connected", 3.minutes) {
          if (!process.isAlive) {
            val message = "Couldn't wait for the driver to connect, it has already exited pid[${process.id}]"
            logError(message)
            throw IllegalStateException(message)
          }
          driverWithoutAwaitedConnection.isConnected
        }
      }.onFailure { t ->
        driverWithoutAwaitedConnection.closeIdeAndWait(1.minutes)
        throw t
      }
    }
    TestNameSynchronizer(driverWithoutAwaitedConnection).start()
    driverWithoutAwaitedConnection
  }

  /**
   * Alias for [useDriverAndCloseIde] to make it possible apply `fun test() = bgRun.test { }` syntax in tests.
   */
  fun <R> test(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true, shutdownHook: Driver.() -> Unit = {}, block: Driver.() -> R) {
    useDriverAndCloseIde(closeIdeTimeout, takeScreenshot, shutdownHook, block)
  }

  open fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true, shutdownHook: Driver.() -> Unit = {}, block: Driver.() -> R): IDEStartResult {
    val ideStartResult: IDEStartResult
    try {
      driver.withContext { block(this) }
    }
    finally {
      catchAll { shutdownHook(driver) }
      ideStartResult = driver.closeIdeAndWait(closeIdeTimeout, takeScreenshot)
    }
    return ideStartResult
  }

  /**
   * Same as [useDriverAndCloseIde] but waits for the IDE to close itself after the run.
   *
   * The IDE is closed on any exception, or if it doesn't close automatically after the block execution completes.
   */
  open fun <R> useDriver(closeIdeTimeout: Duration = 1.minutes, block: Driver.() -> R): IDEStartResult {
    lateinit var ideStartResult: IDEStartResult
    runCatching {
      driver.withContext {
        block(this)
        if (isConnected) takeScreenshot("beforeIdeClosed")
      }
    }.onFailure { e ->
      runCatching {
        driver.exitApplication()
        waitFor(
          timeout = 15.seconds,
          errorMessage = {
            forceKill()
            "Error on exit application via Driver"
          },
        ) { !driver.isConnected }
      }
      throw e
    }.onSuccess {
      ideStartResult = driver.waitToClose(closeIdeTimeout)
    }
    return ideStartResult
  }

  open fun closeIdeAndWait(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true) {
    driver.closeIdeAndWait(closeIdeTimeout, takeScreenshot)
  }

  protected fun Driver.closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean = true): IDEStartResult {
    val logPrefix = "[Closing ${process.id}]"
    try {
      if (isConnected) {
        if (takeScreenshot) {
          takeScreenshot("beforeIdeClosed")
        }
        exitApplication()
        waitFor("$logPrefix Driver is not connected", closeIdeTimeout) { !isConnected }
      }
      else {
        error("$logPrefix Driver is not connected, so it can't exit IDE")
      }
    }
    catch (t: Throwable) {
      logError("$logPrefix Error on exit application via Driver", t)
      forceKill()
    }
    finally {
      try {
        if (isConnected) close()
        waitFor("$logPrefix Process is closed", closeIdeTimeout) { !process.isAlive }
      }
      catch (e: Throwable) {
        logError("$logPrefix Error waiting IDE is closed", e)
        forceKill()
        throw IllegalStateException("$logPrefix Process didn't die after waiting for Driver to close IDE", e)
      }
    }

    @Suppress("TestOnlyProblems")
    return timeoutRunBlocking(5.minutes) {
      startResult.await()
    }
  }

  protected fun Driver.waitToClose(closeIdeTimeout: Duration): IDEStartResult {
    val logPrefix = "[Waiting shutdown ${process.id}]"
    runCatching {
      waitFor("$logPrefix Driver is not connected", closeIdeTimeout) { !isConnected }
    }.onFailure { e ->
      logError("$logPrefix Error on waiting for application exit", e)
      takeScreenshot("beforeIdeKilled")
      forceKill()
    }
    runCatching {
      waitFor("$logPrefix Process is closed", closeIdeTimeout) { !process.isAlive }
    }.onFailure { e ->
      logError("$logPrefix Error waiting IDE is closed", e)
      forceKill()
      throw IllegalStateException("$logPrefix Process didn't die after waiting for Driver to close IDE", e)
    }

    @Suppress("SSBasedInspection")
    return runBlocking {
      startResult.await()
    }
  }

  open fun forceKill() {
    logOutput("[Closing ${process.id}] Performing force kill")
    process.kill()
  }
}
