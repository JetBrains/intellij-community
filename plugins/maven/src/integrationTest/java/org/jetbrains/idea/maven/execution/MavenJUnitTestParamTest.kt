// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.delegation.junit.patternToSurefireSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [patternToSurefireSpec] — no platform initialization required.
 */
class MavenJUnitTestParamTest {

  // ── patternToSurefireSpec ────────────────────────────────────────────────

  @Test
  fun `class-only pattern passes through unchanged`() {
    assertEquals("com.example.MyTest", patternToSurefireSpec("com.example.MyTest"))
  }

  @Test
  fun `wildcard class pattern passes through unchanged`() {
    assertEquals("com.example.*", patternToSurefireSpec("com.example.*"))
  }

  @Test
  fun `comma-separated class and method becomes hash-separated`() {
    assertEquals("com.example.MyTest#myMethod", patternToSurefireSpec("com.example.MyTest,myMethod"))
  }

  @Test
  fun `first comma is used as separator even when method name contains no special chars`() {
    // JUnit pattern "ClassName,method" → Surefire "ClassName#method"
    assertEquals("Foo#bar", patternToSurefireSpec("Foo,bar"))
  }

  @Test
  fun `empty string passes through unchanged`() {
    assertEquals("", patternToSurefireSpec(""))
  }

  @Test
  fun `simple class name without package passes through unchanged`() {
    assertEquals("MyTest", patternToSurefireSpec("MyTest"))
  }
}
