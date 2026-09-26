// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.delegation.testng.buildTestNGTestParam
import com.theoryinpractice.testng.model.TestData
import com.theoryinpractice.testng.model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [buildTestNGTestParam] — no platform initialization required.
 * [TestData] is a plain POJO; [TestType] enum constants do not call platform APIs at load time.
 */
class MavenTestNGTestParamTest {

  private fun data(type: TestType, block: TestData.() -> Unit = {}): TestData {
    val d = TestData()
    d.TEST_OBJECT = type.type
    d.block()
    return d
  }

  // ── CLASS ────────────────────────────────────────────────────────────────

  @Test
  fun `CLASS with class name returns the class name`() {
    val d = data(TestType.CLASS) { MAIN_CLASS_NAME = "com.example.MyTest" }
    assertEquals("com.example.MyTest", buildTestNGTestParam(d))
  }

  @Test
  fun `CLASS with empty class name returns null`() {
    val d = data(TestType.CLASS) { MAIN_CLASS_NAME = "" }
    assertNull(buildTestNGTestParam(d))
  }

  // ── METHOD ───────────────────────────────────────────────────────────────

  @Test
  fun `METHOD produces ClassName#methodName`() {
    val d = data(TestType.METHOD) {
      MAIN_CLASS_NAME = "com.example.MyTest"
      METHOD_NAME = "myTest"
    }
    assertEquals("com.example.MyTest#myTest", buildTestNGTestParam(d))
  }

  @Test
  fun `METHOD with empty class name returns null`() {
    val d = data(TestType.METHOD) {
      MAIN_CLASS_NAME = ""
      METHOD_NAME = "myTest"
    }
    assertNull(buildTestNGTestParam(d))
  }

  @Test
  fun `METHOD with empty method name returns null`() {
    val d = data(TestType.METHOD) {
      MAIN_CLASS_NAME = "com.example.MyTest"
      METHOD_NAME = ""
    }
    assertNull(buildTestNGTestParam(d))
  }

  // ── PACKAGE ──────────────────────────────────────────────────────────────

  @Test
  fun `PACKAGE with package name returns pkg-star pattern`() {
    val d = data(TestType.PACKAGE) { PACKAGE_NAME = "com.example" }
    assertEquals("com.example.*", buildTestNGTestParam(d))
  }

  @Test
  fun `PACKAGE with empty package name returns star`() {
    val d = data(TestType.PACKAGE) { PACKAGE_NAME = "" }
    assertEquals("*", buildTestNGTestParam(d))
  }

  @Test
  fun `PACKAGE with null package name returns star`() {
    val d = data(TestType.PACKAGE) { PACKAGE_NAME = null }
    assertEquals("*", buildTestNGTestParam(d))
  }

  // ── PATTERN ──────────────────────────────────────────────────────────────

  @Test
  fun `PATTERN with single entry returns it unchanged`() {
    val d = data(TestType.PATTERN)
    d.patterns.add("com.example.MyTest")
    assertEquals("com.example.MyTest", buildTestNGTestParam(d))
  }

  @Test
  fun `PATTERN with multiple entries joins with plus`() {
    val d = data(TestType.PATTERN)
    d.patterns.add("com.example.A")
    d.patterns.add("com.example.B")
    assertEquals("com.example.A+com.example.B", buildTestNGTestParam(d))
  }

  @Test
  fun `PATTERN with no entries returns null`() {
    val d = data(TestType.PATTERN)
    assertNull(buildTestNGTestParam(d))
  }

  // ── unsupported types ─────────────────────────────────────────────────────

  @Test
  fun `GROUP returns null`() {
    val d = data(TestType.GROUP) { GROUP_NAME = "smoke" }
    assertNull(buildTestNGTestParam(d))
  }

  @Test
  fun `SUITE returns null`() {
    val d = data(TestType.SUITE) { SUITE_NAME = "testng.xml" }
    assertNull(buildTestNGTestParam(d))
  }
}
