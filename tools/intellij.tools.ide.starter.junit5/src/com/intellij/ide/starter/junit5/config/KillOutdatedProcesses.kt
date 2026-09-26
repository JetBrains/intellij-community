package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.coroutine.CommonScope
import com.intellij.ide.starter.process.findAndKillLeftoverProcessesFromTestRuns
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

@Suppress("RAW_RUN_BLOCKING")  // Can't use `timeoutRunBlocking` here, it's not available in `intellij.tools.plugin.checker.tests`.
open class KillOutdatedProcesses : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext): Unit = runBlocking {
    if (CommonScope.shouldKillOutdatedProcessesBetweenContainers()) {
      findAndKillLeftoverProcessesFromTestRuns()
    }
  }

  override fun afterAll(context: ExtensionContext): Unit = runBlocking {
    if (CommonScope.shouldKillOutdatedProcessesBetweenContainers()) {
      findAndKillLeftoverProcessesFromTestRuns(reportErrors = true, testClassWithLeftoverProcesses = context.requiredTestClass.name)
    }
  }
}



