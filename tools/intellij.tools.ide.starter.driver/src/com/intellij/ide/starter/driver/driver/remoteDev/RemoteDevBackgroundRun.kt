package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.hasVisibleWindow
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.catchAll
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Deferred
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class RemoteDevBackgroundRun(
  val backendRun: BackgroundRun,
  frontendProcess: IDEHandle,
  frontendDriver: Driver,
  frontendStartResult: Deferred<IDEStartResult>,
  frontendRunContext: IDERunContext,
) : BackgroundRun(startResult = frontendStartResult,
                  driverWithoutAwaitedConnection = frontendDriver,
                  process = frontendProcess,
                  runContext = frontendRunContext) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, takeScreenshot: Boolean, shutdownHook: Driver.() -> Unit, block: Driver.() -> R): IDEStartResult {
    val testError = runCatching {
      waitAndPrepareForTest()
      driver.withContext { block(this) }
    }.exceptionOrNull()
    catchAll { shutdownHook(driver) }
    val closeError = runCatching { closeIdeAndWait(closeIdeTimeout, takeScreenshot) }.exceptionOrNull()
    if (testError != null) {
      closeError?.let(testError::addSuppressed)
      throw testError
    }
    closeError?.let { throw it }
    @Suppress("TestOnlyProblems")
    return timeoutRunBlocking(5.minutes) {
      backendRun.startResult.await()
        .also {
          it.frontendStartResult = startResult.await()
        }
    }
  }

  private fun waitAndPrepareForTest() {
    awaitBackendIsConnected()
    awaitVisibleFrameFrontend()
    awaitFrontendReadyForTest()
  }

  protected open fun awaitFrontendReadyForTest() {
    driver.awaitLuxInitialized()
  }

  private fun awaitBackendIsConnected() {
    waitFor("Backend Driver is connected", 3.minutes) { backendRun.driver.isConnected }
  }

  private fun awaitVisibleFrameFrontend() {
    waitFor("Frontend has a visible IDE frame", timeout = 100.seconds) { driver.hasVisibleWindow() }
  }

  @Remote("com.jetbrains.thinclient.lux.LuxClientService", plugin = "com.jetbrains.performancePlugin/intellij.performanceTesting.frontend.split")
  interface LuxClientService {
    fun getMaybeInstance(): LuxClientService?
  }

  /**
   * Needed for compatibility of 262 driver with older version of IDE.
   * e.g. for update tests.
   */
  @Remote("com.jetbrains.thinclient.lux.LuxClientService", plugin = "com.intellij.jetbrains.client.performanceTesting")
  private interface LuxClientServiceFallback: LuxClientService

  fun Driver.awaitLuxInitialized() {
    val luxClientServiceUtility = runCatching { utility(LuxClientService::class).also { it.getMaybeInstance() } }
      .getOrElse { utility(LuxClientServiceFallback::class).also { it.getMaybeInstance() } }
    waitFor("Lux is initialized", timeout = 30.seconds) { luxClientServiceUtility.getMaybeInstance() != null }
  }

  override fun closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean) {
    val frontendError = runCatching { driver.closeIdeAndWait(closeIdeTimeout) }.exceptionOrNull()
    val backendError = runCatching { backendRun.closeIdeAndWait(closeIdeTimeout + 30.seconds, false) }.exceptionOrNull()
    if (frontendError == null) {
      backendError?.let { throw it }
      return
    }
    backendError?.let(frontendError::addSuppressed)
    throw frontendError
  }

  override fun forceKill() {
    backendRun.forceKill()
    super.forceKill()
  }
}
