// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.core.trace.TracingResult
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.exec.UserCodeExceptionTest
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.debugger.streams.test.TraceExecutionFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.xdebugger.XDebugSession
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@TestApplication
class BreakpointEngineUserCodeExceptionTest : UserCodeExceptionTest() {
  override fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    object : BreakpointBasedExecutionTestCaseHelper(trace, session) {
      override fun handleSuccess(chain: StreamChain, result: TracingResult, resultMustBeNull: Boolean?) {
        assertNotNull(result)
        super.handleSuccess(chain, result, resultMustBeNull)
        assertTrue(result.exceptionThrown())
      }
    }
}
