// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper
import com.intellij.debugger.streams.test.TraceExecutionFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.xdebugger.XDebugSession
import org.junit.jupiter.api.Test

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
open class FilteringOperationsTest {
  protected val streamEx: StreamExFixture by streamExFixture("filtering", ::getHelper)

  protected open fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    ExecutionTestCaseHelper(trace, session)

  @Test
  fun testNonNull(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testNonNullKeys(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testNonNullValues(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testWithout(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testGreater(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testAtLeast(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testLess(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testAtMost(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFilterBy(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFilterByKeys(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFilterByValues(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFilterByKeyValue(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testSelect(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testSelectKeys(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testSelectValues(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testTakeWhile(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testTakeWhileInclusive(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testDropWhile(): Unit = streamEx.doStreamExWithResultTest()
}