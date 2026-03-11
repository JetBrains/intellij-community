// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint.streamex

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.exec.streamex.PartialReductionOperationsTest
import com.intellij.debugger.streams.exec.streamex.StreamExTestCase
import com.intellij.idea.IgnoreJUnit3
import com.intellij.xdebugger.XDebugSession
import org.junit.Ignore

class BreakpointEnginePartialReductionOperationsTest : StreamExTestCase() {
  override val packageName: String = "partialReduction"

  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper = breakpointEngineHelper(session)

  fun testGroupRuns() = doStreamExWithResultTest()
  fun testCollapseNoExtraCalls() = doStreamExVoidTest()
  fun testIntervalMapNoExtraCalls() = doStreamExVoidTest()
  fun testCollapseKeys() = doStreamExWithResultTest()

  fun ignoreTestRunLengths() = doStreamExWithResultTest() // for now fails with some odd unrelated exception
}
