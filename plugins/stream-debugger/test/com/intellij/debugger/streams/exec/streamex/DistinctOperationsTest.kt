// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class DistinctOperationsTest : StreamExTestCase() {
  override val packageName: String = "distinct"

  fun testDistinctAtLeast() = doStreamExWithResultTest()

  fun testDistinctWithKeyExtractor() = doStreamExWithResultTest()
  fun testDistinctWithStatefulExtractor() = doStreamExWithResultTest()

  fun testDistinctKeys() = doStreamExWithResultTest()
  fun testDistinctValues() = doStreamExWithResultTest()
}
