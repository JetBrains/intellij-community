// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import org.jetbrains.idea.maven.utils.PrefixStringEncoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PrefixStringEncoderTest {
  @Test
  fun `test string encoding and decoding`() {
    val b = PrefixStringEncoder(setOf("main", "test"), "compileSourceRoot-")
    val expectedResults = listOf<Pair<String, String>> (
      "" to "",
      "myName" to "myName",
      "main" to "compileSourceRoot-main",
      "test" to "compileSourceRoot-test",
      "compileSourceRoot-main" to "compileSourceRoot-compileSourceRoot-main",
      "compileSourceRoot-test" to "compileSourceRoot-compileSourceRoot-test",
    )
    expectedResults.forEach { (from, to) ->
      Assertions.assertEquals(to, b.encode(from), "encode unexpected result")
      Assertions.assertEquals(from, b.decode(to), "decode unexpected result")
    }
  }
}