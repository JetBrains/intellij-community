// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class PrefixOperationsTest {
  private val streamEx by streamExFixture("misc")

  @Test
  fun testPrefix(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testPrefixKeys(): Unit = streamEx.doStreamExVoidTest()

  @Test
  fun testPrefixValues(): Unit = streamEx.doStreamExVoidTest()
}
