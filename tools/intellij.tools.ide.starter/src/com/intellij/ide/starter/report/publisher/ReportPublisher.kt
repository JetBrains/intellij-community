// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.publisher

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext

interface ReportPublisher {
  /**
   * Publish report only if run ide return result @see com.intellij.ide.starter.runner.IDERunContext.runIDE
   */
  fun publishResultOnSuccess(ideStartResult: IDEStartResult)

  /**
   * Publish a report if run threw an exception [throwable]
   * @see com.intellij.ide.starter.runner.IDERunContext.runIDE
   */
  fun publishResultOnException(context: IDERunContext, throwable: Throwable) {}

  /**
   * Publish report even if error occurred during run ide
   */
  fun publishAnywayAfterRun(context: IDERunContext)
}