package com.intellij.ide.starter.report.publisher.impl

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.util.common.logOutput

object ConsoleTestResultPublisher : ReportPublisher {

  override fun publishResultOnSuccess(ideStartResult: IDEStartResult) {
    logOutput(ideStartResult)
  }

  override fun publishAnywayAfterRun(context: IDERunContext) {
    logOutput(context)
  }
}