// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [surefireTestSpec] — no platform initialization required.
 */
class SurefireRerunFailedTestsActionTest {

  // ── surefireTestSpec ──────────────────────────────────────────────────────

  @Test
  fun `dot-separated name becomes hash-separated spec`() {
    assertEquals("com.example.MyTest#myMethod", surefireTestSpec("com.example.MyTest.myMethod"))
  }

  @Test
  fun `last dot is used as separator for deeply nested class`() {
    // "a.b.c.MyTest.myMethod" → last dot separates class from method
    assertEquals("a.b.c.MyTest#myMethod", surefireTestSpec("a.b.c.MyTest.myMethod"))
  }

  @Test
  fun `name without dot returns null`() {
    assertNull(surefireTestSpec("noDotHere"))
  }

  @Test
  fun `simple class-dot-method becomes spec`() {
    assertEquals("MyTest#run", surefireTestSpec("MyTest.run"))
  }

  @Test
  fun `empty string returns null`() {
    assertNull(surefireTestSpec(""))
  }

  @Test
  fun `name that is just a dot returns empty class and empty method`() {
    // Edge case: "." → class="" method="" → "#"
    assertEquals("#", surefireTestSpec("."))
  }
}
