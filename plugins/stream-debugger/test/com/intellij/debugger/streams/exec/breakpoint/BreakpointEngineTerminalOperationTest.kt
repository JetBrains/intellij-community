// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.test.TraceExecutionTestCase
import com.intellij.xdebugger.XDebugSession

class BreakpointEngineTerminalOperationTest : TraceExecutionTestCase() {
  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper = breakpointEngineHelper(session)

  fun testForEachNoExtraCalls(): Unit = doTest(true)

  fun testForEachOrderedNoExtraCalls(): Unit = doTest(true)
}
