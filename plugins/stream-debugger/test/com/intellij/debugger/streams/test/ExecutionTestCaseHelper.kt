// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugSession

open class ExecutionTestCaseHelper(
  val trace: TraceExecutionFixture,
  debugSession: XDebugSession,
) : TraceExecutionTestHelper(debugSession, trace.librarySupportProvider, trace.positionResolver, trace.LOG) {
  override fun getTestName(): String {
    return trace.testName
  }

  override fun print(message: String, processOutputType: Key<*>) {
    trace.debugger.execution.print(message, processOutputType)
  }

  override fun println(message: String, processOutputType: Key<*>) {
    trace.debugger.println(message, processOutputType)
  }

  override fun resume() {
    ApplicationManager.getApplication().invokeLater(Runnable { session.resume() })
  }
}
