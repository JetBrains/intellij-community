package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.process.findAndKillLeftoverProcessesFromTestRuns
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class KillOutdatedProcessesAfterEach : AfterEachCallback {
  override fun afterEach(context: ExtensionContext) {
    findAndKillLeftoverProcessesFromTestRuns()
  }
}



