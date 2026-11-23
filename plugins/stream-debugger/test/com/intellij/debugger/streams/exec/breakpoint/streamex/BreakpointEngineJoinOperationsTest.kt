// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint.streamex

import com.intellij.debugger.streams.exec.streamex.streamExFixture
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
internal class BreakpointEngineJoinOperationsTest {
  private val streamEx by streamExFixture("mapping", ::BreakpointBasedExecutionTestCaseHelper)

  @Test
  fun testJoinNoExtraCalls(): Unit = streamEx.doStreamExVoidTest()
}
