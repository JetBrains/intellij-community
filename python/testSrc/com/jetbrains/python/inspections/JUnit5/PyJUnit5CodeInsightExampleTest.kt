// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5

import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.FolderTest
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.inspections.JUnit5.util.*
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@InspectionTest(PyTypeCheckerInspection::class)
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath("\$CONTENT_ROOT/../testData/junit5/PyTypeCheckerInspection")
class PyJUnit5CodeInsightExampleTest {

  @InjectCodeInsightTestFixture
  lateinit var codeInsightFixture: CodeInsightTestFixture

  @Test
  fun oldStyleTest() {
    codeInsightFixture
      .doTestByText("""
      def foo(x: str) -> str: ...
      x = <warning descr="Expected type 'int', got 'str' instead">foo(1, 2, 3)</warning>
      x = <warning descr="Expected type 'int', got 'str' instead">"123"</warning>
      x: int = <warning descr="Expected type 'int', got 'str' instead">"123"</warning>
    """.trimIndent())
  }

  @Test
  @WithLanguageLevel(LanguageLevel.PYTHON38)
  fun withLanguageLevel() {
    codeInsightFixture.doTestByText(
      """
      x: int = <warning descr="Expected type 'int', got 'str' instead">"123"</warning>
      """.trimIndent()
    )
    assertEquals(LanguageLevel.forElement(codeInsightFixture.file), LanguageLevel.PYTHON38)
  }

  @FolderTest
  fun folderTest(file: PsiFile) {
    codeInsightFixture.doTestByFile(file)
  }

  @Test
  fun testSingle(file: PsiFile) {
    codeInsightFixture.doTestByFile(file)
    assertEquals(LanguageLevel.forElement(file), LanguageLevel.getLatest())
  }

  @ParameterizedTest
  @ValueSource(strings = ["1", "2"])
  fun testParameterized(arg: String) {
    codeInsightFixture
      .doTestByText("""
      def foo(x: str) -> str: ...
      foo(<warning descr="Expected type 'str', got 'int' instead">$arg</warning>)
    """.trimIndent())
  }

  companion object {
    @JvmStatic
    fun arguments() = listOf(
      Arguments.of("'hello'", "str"),
      Arguments.of("3", "int"),
      Arguments.of("True", "bool"),
    )
  }

  @ParameterizedTest
  @MethodSource("arguments")
  fun testWithMethodSource(value: String, type: String) {
    codeInsightFixture
      .doTestByText("""
      def foo(x: $type): ...
      foo($value)
    """.trimIndent())
  }

  @TestFactory
  fun testFactory() = listOf(
    "'hello'" to "str",
    "3" to "int",
    "True" to "bool",
  ).map { (value, type) ->
    DynamicTest.dynamicTest("testFactory($value - $type)") {
      codeInsightFixture
        .doTestByText("""
      def foo(x: $type): ...
      foo($value)
    """.trimIndent())
    }
  }
}