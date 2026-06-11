package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.hasVisibleWindow
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RemoteDevBackgroundRun(
  val backendRun: BackgroundRun,
  frontendProcess: IDEHandle,
  frontendDriver: Driver,
  frontendStartResult: Deferred<IDEStartResult>,
) : BackgroundRun(startResult = frontendStartResult,
                  driverWithoutAwaitedConnection = frontendDriver,
                  process = frontendProcess) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, takeScreenshot: Boolean, shutdownHook: Driver.() -> Unit, block: Driver.() -> R): IDEStartResult {
    try {
      waitAndPrepareForTest()

      driver.withContext { block(this) }
    }
    finally {
      catchAll { shutdownHook(driver) }
      closeIdeAndWait(closeIdeTimeout, takeScreenshot)
    }
    @Suppress("SSBasedInspection")
    return runBlocking {
      backendRun.startResult.await()
        .also {
          it.frontendStartResult = startResult.await()
        }
    }
  }

  private fun waitAndPrepareForTest() {
    awaitBackendIsConnected()
    awaitVisibleFrameFrontend()
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
    try {
      driver.closeIdeAndWait(closeIdeTimeout)
    }
    finally {
      backendRun.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
    }
  }

  override fun forceKill() {
    backendRun.forceKill()
    super.forceKill()
  }
}
