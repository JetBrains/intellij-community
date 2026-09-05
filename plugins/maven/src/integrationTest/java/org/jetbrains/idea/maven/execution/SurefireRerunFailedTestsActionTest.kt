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
  fun `name that is just a dot returns null because method is empty`() {
    // "." → class="" method="" → stripped method is empty → null (not a valid spec)
    assertNull(surefireTestSpec("."))
  }

  // ── parameterized test names ───────────────────────────────────────────────

  @Test
  fun `JUnit 5 name with type and index suffix strips to base method`() {
    // Surefire XML: name="testFoo(String)[1] hello"
    assertEquals("com.example.MyTest#testFoo", surefireTestSpec("com.example.MyTest.testFoo(String)[1] hello"))
  }

  @Test
  fun `name with bracket index only strips to base method`() {
    assertEquals("com.example.MyTest#testFoo", surefireTestSpec("com.example.MyTest.testFoo[0]"))
  }

  @Test
  fun `name with space before bracket strips trailing space`() {
    assertEquals("com.example.MyTest#testFoo", surefireTestSpec("com.example.MyTest.testFoo [1]"))
  }

  @Test
  fun `short invocation leaf name with no dot returns null`() {
    assertNull(surefireTestSpec("[1]"))
  }

  // ── surefireSpecFromLocation ──────────────────────────────────────────────

  @Test
  fun `java test location URL produces class-hash-method spec`() {
    assertEquals(
      "com.example.MyTest#myMethod",
      surefireSpecFromLocation("java:test://com.example.MyTest/myMethod"),
    )
  }

  @Test
  fun `java suite location URL returns null`() {
    assertNull(surefireSpecFromLocation("java:suite://com.example.MyTest"))
  }

  @Test
  fun `null location URL returns null`() {
    assertNull(surefireSpecFromLocation(null))
  }

  @Test
  fun `location URL without slash returns null`() {
    assertNull(surefireSpecFromLocation("java:test://com.example.MyTest"))
  }
}
