// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class ConcatenateOperationsTest : StreamExTestCase() {
  override val packageName: String = "concatenate"

  fun testAppendToEmpty() = doStreamExWithResultTest()
  fun testAppendNone() = doStreamExWithResultTest()
  fun testAppendOne() = doStreamExWithResultTest()
  fun testAppendMany() = doStreamExWithResultTest()

  fun testPrependToEmpty() = doStreamExWithResultTest()
  fun testPrependNone() = doStreamExWithResultTest()
  fun testPrependOne() = doStreamExWithResultTest()
  fun testPrependMany() = doStreamExWithResultTest()
}