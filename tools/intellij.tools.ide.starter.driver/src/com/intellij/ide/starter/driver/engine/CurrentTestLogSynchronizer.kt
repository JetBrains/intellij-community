package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.setLogDir
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.TestMethod

/**
 * Keeps the remote IDE's log dirs in sync with [CurrentTestMethod].
 *
 * The listener self-removes on a driver failure with no successor (e.g. IDE disconnect with no restart).
 */
internal class CurrentTestLogSynchronizer(
  private val isConnected: () -> Boolean,
  private val syncLogDir: () -> Unit,
) {

  constructor(driver: Driver, runContext: IDERunContext) : this(
    isConnected = { driver.isConnected },
    syncLogDir = {
      if (driver.getProductVersion().baselineVersion >= 263) {
        runContext.registerNewIdeReportingData { logsDir ->
          driver.setLogDir(logsDir)
        }
      }
    },
  )

  private val listener: (TestMethod?) -> Unit = listener@{ testMethod ->
    if (isConnected()) {
      if (testMethod != null) {
        syncLogDir()
      }
    }
    else {
      stop()
    }
  }

  /** Registers the listener. [CurrentTestMethod.addOnChangeListener] immediately fires it with the
   *  current test method, so call this only once the driver is already connected. */
  fun start() {
    CurrentTestMethod.addOnChangeListener(listener)
  }

  private fun stop() {
    CurrentTestMethod.removeOnChangeListener(listener)
  }
}
