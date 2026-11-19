// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.breakpoint.streamex

import com.intellij.debugger.streams.exec.streamex.streamExFixture
import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class BreakpointEngineSortedOperationsTest {
  private val streamEx by streamExFixture("sorted", ::BreakpointBasedExecutionTestCaseHelper)

  @Test
  fun testSortedByNoExtraCalls(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedByIntNoExtraCalls(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedByLongNoExtraCalls(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedByDoubleNoExtraCalls(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testReverseSortedNoExtraCalls(): Unit = streamEx.doStreamExVoidTest()
}
