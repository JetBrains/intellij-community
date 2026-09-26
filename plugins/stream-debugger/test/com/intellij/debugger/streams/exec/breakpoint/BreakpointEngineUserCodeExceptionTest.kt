// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.core.trace.TracingResult
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.exec.UserCodeExceptionTest
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.xdebugger.XDebugSession

class BreakpointEngineUserCodeExceptionTest : UserCodeExceptionTest() {
  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper =
    object : BreakpointBasedExecutionTestCaseHelper(this, session, getLibrarySupportProvider(), myPositionResolver, LOG) {
      override fun handleSuccess(chain: StreamChain, result: TracingResult, resultMustBeNull: Boolean?) {
        assertNotNull(result)
        super.handleSuccess(chain, result, resultMustBeNull)
        assertTrue(result.exceptionThrown())
      }
    }
}
