// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class FilteringOperationsTest : StreamExTestCase() {
  override val packageName: String = "filtering"

  fun testNonNull() = doStreamExWithResultTest()

  fun testRemove() = doStreamExVoidTest()
  fun testRemoveBy() = doStreamExVoidTest()
  fun testRemoveKeys() = doStreamExWithResultTest()
  fun testRemoveValues() = doStreamExWithResultTest()
  fun testRemoveKeyValue() = doStreamExWithResultTest()

  fun testNonNullKeys() = doStreamExWithResultTest()
  fun testNonNullValues() = doStreamExWithResultTest()

  fun testWithout() = doStreamExWithResultTest()

  fun testGreater() = doStreamExWithResultTest()
  fun testAtLeast() = doStreamExWithResultTest()
  fun testLess() = doStreamExWithResultTest()
  fun testAtMost() = doStreamExWithResultTest()

  fun testFilterBy() = doStreamExWithResultTest()
  fun testFilterByKeys() = doStreamExWithResultTest()
  fun testFilterByValues() = doStreamExWithResultTest()
  fun testFilterByKeyValue() = doStreamExWithResultTest()

  fun testSelect() = doStreamExWithResultTest()
  fun testSelectKeys() = doStreamExWithResultTest()
  fun testSelectValues() = doStreamExWithResultTest()

  fun testTakeWhile() = doStreamExWithResultTest()
  fun testTakeWhileInclusive() = doStreamExWithResultTest()
  fun testDropWhile() = doStreamExWithResultTest()
}