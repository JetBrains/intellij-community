// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec

import com.intellij.debugger.streams.test.BreakpointBasedExecutionTestCaseHelper
import com.intellij.debugger.streams.test.traceExecutionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class GatherOperationsTest {
  private val traceFixture by traceExecutionFixture(::BreakpointBasedExecutionTestCaseHelper, languageLevel = LanguageLevel.JDK_25)

  @Test
  fun testGatherWindowFixed() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherWindowSliding() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherScan() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherFold() {
    traceFixture.doTest(false)
  }

  /**
   * The test data uses `maxConcurrency = 1` on purpose: with a larger value `Gatherers.mapConcurrent` pushes every
   * already-completed task on each `integrate()` call, so the recorded times depend on virtual thread timings and the test becomes flaky.
   */
  @Test
  fun testGatherMapConcurrent() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomOneToOne() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomOneToManyPerElement() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomBufferThenFlush() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomConsumeAll() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomEmpty() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomLimit() {
    traceFixture.doTest(false)
  }

  @Test
  fun testGatherCustomTakeWhile() {
    traceFixture.doTest(false)
  }
}
