package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.withIndent
import com.intellij.tools.ide.util.common.logOutput
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

/**
 * The listener prints free space available to the test
 */
open class FreeSpacePrinter : TestExecutionListener {

  override fun executionStarted(testIdentifier: TestIdentifier?) {
    if (testIdentifier?.isTest != true) {
      return
    }

    if (CIServer.instance.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testIdentifier.displayName}")
        appendLine(FileSystem.getDiskUsageDiagnostics().withIndent("  "))
      })
    }
  }
}