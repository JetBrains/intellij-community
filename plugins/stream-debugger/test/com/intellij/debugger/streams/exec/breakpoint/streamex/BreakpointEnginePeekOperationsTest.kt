// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint.streamex

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.exec.streamex.PeekOperationsTest
import com.intellij.debugger.streams.exec.streamex.StreamExTestCase
import com.intellij.xdebugger.XDebugSession

class BreakpointEnginePeekOperationsTest : StreamExTestCase() {
  override val packageName: String = "peek"

  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper = breakpointEngineHelper(session)

  fun testPeekNoExtraCalls() = doStreamExWithResultTest()
}
