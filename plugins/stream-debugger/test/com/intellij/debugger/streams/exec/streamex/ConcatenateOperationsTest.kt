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
open class ConcatenateOperationsTest {
  protected val streamEx: StreamExFixture by streamExFixture("concatenate", ::getHelper)

  protected open fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    ExecutionTestCaseHelper(trace, session)

  @Test
  fun testAppendToEmpty(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testAppendNone(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testAppendOne(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testAppendMany(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testPrependToEmpty(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testPrependNone(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testPrependOne(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testPrependMany(): Unit = streamEx.doStreamExVoidTest()
}