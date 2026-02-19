package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.afterEachMessageBusCleanup
import com.intellij.ide.starter.junit5.StarterBusTestPlanListener.Companion.isServerRunning
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class StarterBusEachTestCallback : BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(context: ExtensionContext?) {
    if (isServerRunning.get()) return
    try {
      EventsBus.startServerProcess()
      isServerRunning.set(true)
    }
    catch (_: Throwable) {
    }
  }

  override fun afterEach(context: ExtensionContext?) {
    if (ConfigurationStorage.afterEachMessageBusCleanup()) {
      EventsBus.unsubscribeAll()
    }
  }
}