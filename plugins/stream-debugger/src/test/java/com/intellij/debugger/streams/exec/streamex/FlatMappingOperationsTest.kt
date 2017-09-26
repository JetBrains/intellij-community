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
