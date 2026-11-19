// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
class PeekOperationsTest {
  private val streamEx by streamExFixture("peek")

  @Test
  fun testPeek(): Unit = streamEx.doStreamExWithResultTest()
}
