// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class MiscIntermediateOperationsTest : StreamExTestCase() {
  override val packageName: String = "misc"

  fun testHeadTail() = doStreamExWithResultTest()

  fun testChain() = doStreamExWithResultTest()

  fun testSkipOrdered() = doStreamExWithResultTest()

  fun testParallel() = doStreamExVoidTest()

  fun testZipWithSameSizes() = doStreamExVoidTest()
  fun testZipWithLesser() = doStreamExVoidTest()
  fun testZipWithGreater() = doStreamExVoidTest()

  fun testPrefix() = doStreamExVoidTest()
  fun testPrefixKeys() = doStreamExVoidTest()
  fun testPrefixValues() = doStreamExVoidTest()
}