package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.utils.catchAll
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
    driverWithoutAwaitedConnection
  }

  /**
   * Alias for [useDriverAndCloseIde] to make it possible apply `fun test() = bgRun.test { }` syntax in tests.
   */
  fun <R> test(closeIdeTimeout: Duration = 1.minutes, shutdownHook: Driver.() -> Unit = {}, block: Driver.() -> R) {
    useDriverAndCloseIde(closeIdeTimeout, shutdownHook, block)
  }

  open fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration = 1.minutes, shutdownHook: Driver.() -> Unit = {}, block: Driver.() -> R): IDEStartResult {
    val ideStartResult: IDEStartResult
    try {
      driver.withContext { block(this) }
    }
    finally {
      catchAll { shutdownHook(driver) }
      ideStartResult = driver.closeIdeAndWait(closeIdeTimeout)
    }
    return ideStartResult
  }

  open fun closeIdeAndWait(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true) {
    driver.closeIdeAndWait(closeIdeTimeout, takeScreenshot)
  }

  protected fun Driver.closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean = true): IDEStartResult {
    try {
      if (isConnected) {
        if (takeScreenshot) {
          takeScreenshot("beforeIdeClosed")
        }
        exitApplication()
        waitFor("Driver is not connected", closeIdeTimeout, 3.seconds) { !isConnected }
      }
      else {
        error("Driver is not connected, so it can't exit IDE")
      }
    }
    catch (t: Throwable) {
      logError("Error on exit application via Driver", t)
      forceKill()
    }
    finally {
      try {
        if (isConnected) close()
        waitFor("Process is closed", closeIdeTimeout, 3.seconds) { !process.isAlive }
      }
      catch (e: Throwable) {
        logError("Error waiting IDE is closed: ${e.message}: ${e.stackTraceToString()}", e)
        forceKill()
        throw IllegalStateException("Process didn't die after waiting for Driver to close IDE", e)
      }
    }

    @Suppress("SSBasedInspection")
    return runBlocking {
      startResult.await()
    }
  }

  private fun forceKill() {
    logOutput("Performing force kill")
    process.kill()
  }
}