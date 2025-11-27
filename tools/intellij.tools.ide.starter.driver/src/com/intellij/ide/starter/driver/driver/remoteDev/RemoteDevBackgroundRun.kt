package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.hasVisibleWindow
import com.intellij.driver.sdk.ui.IdeEventQueue
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
  private val backendRun: BackgroundRun,
  frontendProcess: IDEHandle,
  frontendDriver: Driver,
  frontendStartResult: Deferred<IDEStartResult>,
) : BackgroundRun(startResult = frontendStartResult,
                  driverWithoutAwaitedConnection = frontendDriver,
                  process = frontendProcess) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, shutdownHook: Driver.() -> Unit, block: Driver.() -> R): IDEStartResult {
    try {
      waitAndPrepareForTest()

      driver.withContext { block(this) }
    }
    finally {
      catchAll { shutdownHook(driver) }
      closeIdeAndWait(closeIdeTimeout)
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
    flushEdt()
  }

  private fun awaitBackendIsConnected() {
    waitFor("Backend Driver is connected", 3.minutes) { backendRun.driver.isConnected }
  }

  private fun awaitVisibleFrameFrontend() {
    waitFor("Frontend has a visible IDE frame", timeout = 100.seconds) { driver.hasVisibleWindow() }
  }

  private fun flushEdt() {
    // FrontendToolWindowHost should finish it's work to avoid https://youtrack.jetbrains.com/issue/GTW-9730/Some-UI-tests-are-flaky-because-sometimes-actions-are-not-executed
    driver.withContext(OnDispatcher.EDT) {
      driver.utility(IdeEventQueue::class).getInstance().flushQueue()
    }
  }

  @Remote("com.jetbrains.thinclient.lux.LuxClientService")
  interface LuxClientService {
    fun getMaybeInstance(): LuxClientService?
  }

  fun Driver.awaitLuxInitialized() {
    waitFor("Lux is initialized", timeout = 30.seconds) { utility(LuxClientService::class).getMaybeInstance() != null }
  }

  override fun closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean) {
    try {
      driver.closeIdeAndWait(closeIdeTimeout)
    }
    finally {
      backendRun.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
    }
  }
}