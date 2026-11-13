/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.asIdentifiers
import com.intellij.platform.uast.testFramework.common.asRefNames
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UFile
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class JavaIdentifiersTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { testName, file -> check(testName, file) }

  private fun getTestFile(testName: String, ext: String) =
    File(uast.fixture.testDataPath, testName.substringBeforeLast('.') + '.' + ext)

  private fun check(testName: String, file: UFile) {
    assertEqualsToFile("Identifiers", getTestFile(testName, "identifiers.txt"), file.asIdentifiers())
    assertEqualsToFile("refNames", getTestFile(testName, "refNames.txt"), file.asRefNames())
  }

  @Test
  fun testAnnotation(): Unit = uast.doTest("Simple/Annotation.java")

  @Test
  fun testAnonymous(): Unit = uast.doTest("Simple/Anonymous.java")

  @Test
  fun testComments(): Unit = uast.doTest("Simple/Comments.java")

  @Test
  fun testTryWithResources(): Unit = uast.doTest("Simple/TryWithResources.java")

  @Test
  fun testInnerClass(): Unit = uast.doTest("Simple/InnerClass.java")

  @Test
  fun testComplexCalls(): Unit = uast.doTest("Simple/ComplexCalls.java")

  @Test
  fun testRecord(): Unit = uast.doTest("Simple/Record.java")

}