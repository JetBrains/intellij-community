// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Vitaliy.Bibaev
 */
class MappingOperationsTest : StreamExTestCase() {
  override val packageName: String = "mapping"

  fun testElements() = doStreamExWithResultTest()

  fun testMapToEntry() = doStreamExWithResultTest()
  fun testMapKeys() = doStreamExVoidTest()
  fun testMapToKey() = doStreamExVoidTest()
  fun testMapValues() = doStreamExVoidTest()
  fun testMapToValue() = doStreamExVoidTest()
  fun testMapKeyValue() = doStreamExWithResultTest()

  fun testInvert() = doStreamExVoidTest()

  fun testKeys() = doStreamExVoidTest()
  fun testValues() = doStreamExWithResultTest()

  fun testJoin() = doStreamExVoidTest()

  fun testPairMap() = doStreamExWithResultTest()

  fun testMapFirst() = doStreamExWithResultTest()
  fun testMapLast() = doStreamExWithResultTest()
  fun testMapFirstOrElse() = doStreamExWithResultTest()
  fun testMapLastOrElse() = doStreamExWithResultTest()

  fun testWithFirst() = doStreamExVoidTest()
}