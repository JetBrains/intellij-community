// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.debugger.streams.test.traceExecutionFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class BreakpointEngineMatchTest {
  private val traceFixture by traceExecutionFixture(::BreakpointBasedExecutionTestCaseHelper)

  @Test
  fun testAllMatchNoExtraCalls(): Unit = traceFixture.doTest(false)

  @Test
  fun testAnyMatchNoExtraCalls(): Unit = traceFixture.doTest(false)

  @Test
  fun testNoneMatchNoExtraCalls(): Unit = traceFixture.doTest(false)
}
