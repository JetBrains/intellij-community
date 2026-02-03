// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class ConcatenateOperationsTest : StreamExTestCase() {
  override val packageName: String = "concatenate"

  fun testAppendToEmpty() = doStreamExVoidTest()
  fun testAppendNone() = doStreamExVoidTest()
  fun testAppendOne() = doStreamExVoidTest()
  fun testAppendMany() = doStreamExVoidTest()

  fun testPrependToEmpty() = doStreamExVoidTest()
  fun testPrependNone() = doStreamExVoidTest()
  fun testPrependOne() = doStreamExVoidTest()
  fun testPrependMany() = doStreamExVoidTest()
}