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
open class MappingOperationsTest {
  protected val streamEx: StreamExFixture by streamExFixture("mapping", ::getHelper)

  protected open fun getHelper(trace: TraceExecutionFixture, session: XDebugSession): TraceExecutionTestHelper =
    ExecutionTestCaseHelper(trace, session)

  @Test
  fun testElements(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testMapToEntry(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testMapKeys(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testMapToKey(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testMapValues(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testMapToValue(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testMapKeyValue(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testInvert(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testKeys(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testValues(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testPairMap(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testMapFirst(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testMapLast(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testMapFirstOrElse(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testMapLastOrElse(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testWithFirst(): Unit = streamEx.doStreamExVoidTest()
}