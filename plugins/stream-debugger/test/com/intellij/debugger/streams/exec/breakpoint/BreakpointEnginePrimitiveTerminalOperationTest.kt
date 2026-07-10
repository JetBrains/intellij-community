// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.core.trace.PrimitiveValue
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.debugger.streams.exec.PrimitiveTerminalOperationTest
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.xdebugger.XDebugSession
import org.junit.Assert

class BreakpointEnginePrimitiveTerminalOperationTest : PrimitiveTerminalOperationTest() {
  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper {
    return object : BreakpointBasedExecutionTestCaseHelper(this, session, getLibrarySupportProvider(), myPositionResolver, LOG) {
      override fun handleResultValue(result: Value?, mustBeNull: Boolean) {
        Assert.assertFalse(mustBeNull)
        Assert.assertNotNull(result)
        Assert.assertTrue(result is PrimitiveValue)
        println("Result type:" + result!!.typeName(), ProcessOutputTypes.SYSTEM)
        println("value = " + result.toString(), ProcessOutputTypes.SYSTEM)
      }
    }
  }
}
