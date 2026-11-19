// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.debugger.streams.test.traceExecutionFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class BreakpointEngineTerminalOperationTest {
  private val traceFixture by traceExecutionFixture(::BreakpointBasedExecutionTestCaseHelper)

  @Test
  fun testForEachNoExtraCalls(): Unit = traceFixture.doTest(true)

  @Test
  fun testForEachOrderedNoExtraCalls(): Unit = traceFixture.doTest(true)
}
