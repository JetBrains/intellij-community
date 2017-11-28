// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class FlatMappingOperationsTest : StreamExTestCase() {
  override val packageName: String = "flatMapping"

  fun testFlatMapToInt() = doStreamExWithResultTest()
  fun testFlatMapToLong() = doStreamExWithResultTest()
  fun testFlatMapToDouble() = doStreamExWithResultTest()
  fun testFlatMapToObj() = doStreamExWithResultTest()
  fun testFlatMapToEntry() = doStreamExWithResultTest()

  fun testFlatCollection() = doStreamExWithResultTest()
  fun testFlatArray() = doStreamExWithResultTest()

  fun testCross() = doStreamExWithResultTest()

  fun testFlatMapKeys() = doStreamExWithResultTest()
  fun testFlatMapToKey() = doStreamExWithResultTest()
  fun testFlatMapValues() = doStreamExWithResultTest()
  fun testFlatMapToValue() = doStreamExWithResultTest()
  fun testFlatMapKeyValue() = doStreamExWithResultTest()
}
