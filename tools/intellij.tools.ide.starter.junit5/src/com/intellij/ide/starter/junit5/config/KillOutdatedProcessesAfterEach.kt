package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.process.findAndKillLeftoverProcessesFromTestRuns
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@Suppress("RAW_RUN_BLOCKING")  // Can't use `timeoutRunBlocking` here, it's not available in `intellij.tools.plugin.checker.tests`.
open class KillOutdatedProcessesAfterEach : AfterEachCallback {
  override fun afterEach(context: ExtensionContext): Unit = runBlocking {
    findAndKillLeftoverProcessesFromTestRuns()
  }
}



