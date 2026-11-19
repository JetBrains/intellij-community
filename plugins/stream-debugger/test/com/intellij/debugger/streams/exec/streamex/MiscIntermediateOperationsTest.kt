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
open class MiscIntermediateOperationsTest {
  protected val streamEx: StreamExFixture by streamExFixture("misc", ::getHelper)

  protected open fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    ExecutionTestCaseHelper(trace, session)

  @Test
  fun testHeadTail(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testChain(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testParallel(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testZipWithSameSizes(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testZipWithLesser(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testZipWithGreater(): Unit = streamEx.doStreamExVoidTest()
}