/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.exec.streamex

import com.intellij.debugger.streams.exec.TraceExecutionTestCase

/**
 * @author Vitaliy.Bibaev
 */
class MappingOperationsTest : StreamExTestCase() {
  override val packageName: String = "mapping"

  fun testElements() = doStreamExWithResultTest()

  fun testMapToEntry() = doStreamExWithResultTest()
  fun testMapKeys() = doStreamExWithResultTest()
  fun testMapToKey() = doStreamExWithResultTest()
  fun testMapValues() = doStreamExWithResultTest()
  fun testMapToValue() = doStreamExWithResultTest()
  fun testMapKeyValue() = doStreamExWithResultTest()

  fun testInvert() = doStreamExWithResultTest()

  fun testKeys() = doStreamExWithResultTest()
  fun testValues() = doStreamExWithResultTest()

  fun testJoin() = doStreamExVoidTest()

  fun testPairMap() = doStreamExWithResultTest()

  fun testMapFirst() = doStreamExWithResultTest()
  fun testMapLast() = doStreamExWithResultTest()
  fun testMapFirstOrElse() = doStreamExWithResultTest()
  fun testMapLastOrElse() = doStreamExWithResultTest()

  fun testWithFirst() = doStreamExWithResultTest()
}