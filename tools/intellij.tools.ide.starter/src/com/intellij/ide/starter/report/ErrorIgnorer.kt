package com.intellij.ide.starter.report

import com.intellij.ide.starter.runner.IDERunContext

/**
 * Does nothing with erorrs reported in IDE
 */
object ErrorIgnorer: ErrorReporter {
  override fun reportErrorsAsFailedTests(runContext: IDERunContext) {}
}
