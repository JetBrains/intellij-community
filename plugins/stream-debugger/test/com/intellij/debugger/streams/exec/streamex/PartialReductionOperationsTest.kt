// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class PartialReductionOperationsTest {
  private val streamEx by streamExFixture("partialReduction")

  @Test
  fun testGroupRuns(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testCollapse(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testIntervalMap(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testRunLengths(): Unit = streamEx.doStreamExWithResultTest()

  @Test
  fun testCollapseKeys(): Unit = streamEx.doStreamExWithResultTest()
}
