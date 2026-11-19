// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.exec.AccessibilityTest
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.debugger.streams.test.TraceExecutionFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.xdebugger.XDebugSession

@TestApplication
class BreakpointEngineAccessibilityTest : AccessibilityTest() {
  override fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    BreakpointBasedExecutionTestCaseHelper(trace, session)
}
