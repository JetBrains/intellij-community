package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.process.findAndKillLeftoverProcessesFromTestRuns
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class KillOutdatedProcesses : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    findAndKillLeftoverProcessesFromTestRuns()
  }
  
  override fun afterAll(context: ExtensionContext) {
    findAndKillLeftoverProcessesFromTestRuns(reportErrors = true)
  }
}



