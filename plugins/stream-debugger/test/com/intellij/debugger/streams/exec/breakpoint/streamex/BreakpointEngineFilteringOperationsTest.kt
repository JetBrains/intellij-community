// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint.streamex

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.exec.streamex.FilteringOperationsTest
import com.intellij.xdebugger.XDebugSession

class BreakpointEngineFilteringOperationsTest : FilteringOperationsTest() {
  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper = breakpointEngineHelper(session)
}
