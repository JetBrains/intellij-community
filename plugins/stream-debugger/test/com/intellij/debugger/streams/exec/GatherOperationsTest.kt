// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper
import com.intellij.debugger.streams.test.TraceExecutionTestCase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.xdebugger.XDebugSession

class GatherOperationsTest : TraceExecutionTestCase() {
  override fun setUpModule() {
    super.setUpModule()
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_25)
  }

  override fun getHelper(session: XDebugSession): TraceExecutionTestHelper = breakpointEngineHelper(session)

  fun testGatherWindowFixed() {
    doTest(false)
  }

  fun testGatherWindowSliding() {
    doTest(false)
  }

  fun testGatherScan() {
    doTest(false)
  }

  fun testGatherFold() {
    doTest(false)
  }

  /**
   * The test data uses `maxConcurrency = 1` on purpose: with a larger value `Gatherers.mapConcurrent` pushes every
   * already-completed task on each `integrate()` call, so the recorded times depend on virtual thread timings and the test becomes flaky.
   */
  fun testGatherMapConcurrent() {
    doTest(false)
  }

  fun testGatherCustomOneToOne() {
    doTest(false)
  }

  fun testGatherCustomOneToManyPerElement() {
    doTest(false)
  }

  fun testGatherCustomBufferThenFlush() {
    doTest(false)
  }

  fun testGatherCustomConsumeAll() {
    doTest(false)
  }

  fun testGatherCustomEmpty() {
    doTest(false)
  }

  fun testGatherCustomLimit() {
    doTest(false)
  }

  fun testGatherCustomTakeWhile() {
    doTest(false)
  }
}
