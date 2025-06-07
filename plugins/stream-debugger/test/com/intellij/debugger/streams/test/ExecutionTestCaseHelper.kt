// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.testFramework.UsefulTestCase
import com.intellij.xdebugger.XDebugSession

open class ExecutionTestCaseHelper(
  val testCase: ExecutionTestCase,
  private val mySession: XDebugSession,
  myLibrarySupportProvider: LibrarySupportProvider,
  myDebuggerPositionResolver: DebuggerPositionResolver,
  logger: Logger,
) : TraceExecutionTestHelper(mySession, myLibrarySupportProvider, myDebuggerPositionResolver, logger) {
  override fun getTestName(): String {
    return UsefulTestCase.getTestName(testCase.name, false)
  }

  override fun print(message: String, processOutputType: Key<*>) {
    testCase.print(message, processOutputType)
  }

  override fun println(message: String, processOutputType: Key<*>) {
    testCase.println(message, processOutputType)
  }

  override fun resume() {
    ApplicationManager.getApplication().invokeLater(Runnable { mySession.resume() })
  }
}