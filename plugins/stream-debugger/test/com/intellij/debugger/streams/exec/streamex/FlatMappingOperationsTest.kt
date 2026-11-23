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
open class FlatMappingOperationsTest {
  protected val streamEx: StreamExFixture by streamExFixture("flatMapping", ::getHelper)

  protected open fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    ExecutionTestCaseHelper(trace, session)

  @Test
  fun testFlatMapToInt(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapToLong(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapToDouble(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapToObj(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapToEntry(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatCollection(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatArray(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testCross(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapKeys(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapToKey(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapValues(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapToValue(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testFlatMapKeyValue(): Unit = streamEx.doStreamExWithResultTest()
}
