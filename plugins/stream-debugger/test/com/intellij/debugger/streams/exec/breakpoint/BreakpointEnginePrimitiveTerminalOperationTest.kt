// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.core.trace.PrimitiveValue
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.debugger.streams.exec.PrimitiveTerminalOperationTest
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.debugger.streams.test.TraceExecutionFixture
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.xdebugger.XDebugSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@TestApplication
class BreakpointEnginePrimitiveTerminalOperationTest : PrimitiveTerminalOperationTest() {
  override fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper {
    return object : BreakpointBasedExecutionTestCaseHelper(trace, session) {
      override fun handleResultValue(result: Value?, mustBeNull: Boolean) {
        assertFalse(mustBeNull)
        assertNotNull(result)
        assertTrue(result is PrimitiveValue)
        println("Result type:" + result!!.typeName(), ProcessOutputTypes.SYSTEM)
        println("value = " + result.toString(), ProcessOutputTypes.SYSTEM)
      }
    }
  }
}
