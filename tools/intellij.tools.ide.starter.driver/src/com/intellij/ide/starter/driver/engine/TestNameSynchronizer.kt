package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.setActiveTestNameInTestIde
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestMethod

/**
 * Keeps the remote IDE's [com.jetbrains.performancePlugin.TestContext] in sync with [CurrentTestMethod].
 *
 * The listener self-removes on the first driver failure (e.g. IDE disconnect or restart).
 */
internal class TestNameSynchronizer(private val driver: Driver) {

  private val listener: (TestMethod?) -> Unit = { method ->
    // active test name setting is supported only for 262+ version
    if (driver.isConnected && driver.getProductVersion().baselineVersion >= 262) {
      driver.setActiveTestNameInTestIde(method?.displayName)
    }
    else {
      CurrentTestMethod.removeOnChangeListener(listener)
    }
  }

  /** Registers the listener. [CurrentTestMethod.addOnChangeListener] immediately fires it with the
   *  current test method, so call this only once the driver is already connected. */
  fun start() {
    CurrentTestMethod.addOnChangeListener(listener)
  }
}
