// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
class SortedOperationsTest {
  private val streamEx by streamExFixture("sorted")

  @Test
  fun testReverseSorted(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedBy(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedByInt(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedByLong(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testSortedByDouble(): Unit = streamEx.doStreamExVoidTest()
}