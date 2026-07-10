// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.exec.IntermediateOperationResolveTest
import com.intellij.xdebugger.XDebugSession

class BreakpointEngineIntermediateOperationResolveTest : IntermediateOperationResolveTest() {
  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper = breakpointEngineHelper(session)
}
