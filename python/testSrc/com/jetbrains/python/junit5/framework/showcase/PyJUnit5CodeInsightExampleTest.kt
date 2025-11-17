// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.showcase

import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.FolderTest
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.junit5.framework.annotations.InjectCodeInsightTestFixture
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.MultiFileTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.junit5.framework.annotations.WithLanguageLevel
import com.jetbrains.python.junit5.framework.util.doTestByFile
import com.jetbrains.python.junit5.framework.util.doTestByText
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath("\$CONTENT_ROOT/../testData/junit5/showcase/PyTypeCheckerInspection")
@PyCodeInsightTestApplication
@InspectionTest(PyTypeCheckerInspection::class)
class PyJUnit5CodeInsightExampleTest {

  /**
   * CodeInsightTestFixture can be injected in class-level field or
   * directly into the test method (see tests below)
   */
  @InjectCodeInsightTestFixture
  lateinit var codeInsightFixture: CodeInsightTestFixture

  @Test
  fun testInjectedFixture(fixture: CodeInsightTestFixture) {
    fixture.doTestByText("""
      x: int = <warning descr="Expected type 'int', got 'str' instead">"123"</warning>
    """.trimIndent())
  }

  /**
   * An example of an old-style `doTestByText` test where no file is involved.
   */
  @Test
  fun oldStyleTestInPlaceTest() {
    codeInsightFixture.doTestByText("""
      x: int = <warning descr="Expected type 'int', got 'str' instead">"123"</warning>
    """.trimIndent())
  }

  /**
   * Test file name can be overridden by [TestMetaInfo] annotation.
   * In this case, the test name is not important.
   */
  @Test
  @TestMetaInfo("customTestName.py")
  fun `single-file test with overridden name`(mainFile: PsiFile) {
    codeInsightFixture.doTestByFile(mainFile)
  }

  /**
   * By default, a test file will be searched in the test directory, defined in [TestDataPath] annotation
   * in testSingle -> single.py format.
   */
  @Test
  fun testSingle(file: PsiFile) {
    codeInsightFixture.doTestByFile(file)
    Assertions.assertEquals(LanguageLevel.forElement(file), LanguageLevel.getLatest())
  }

  /**
   * [MultiFileTest] annotation can be used to define that all the files from the directory should
   * be added to the test fixture.
   * The main file should be named as `a.py` by default but can be overridden (see the test below).
   */
  @MultiFileTest
  fun multiFileTest(mainFile: PsiFile) {
    codeInsightFixture.doTestByFile(mainFile)
  }

  /**
   * Multi-file tests can also override the path to the test file.
   * In this case, the subdirectory containing the test file will be copied to the temporary test directory.
   */
  @MultiFileTest
  @TestMetaInfo("sub/testCase/a.py")
  fun `multi-file test with overridden test dir`(mainFile: PsiFile) {
    codeInsightFixture.doTestByFile(mainFile)
  }

  /**
   * Language level can be overridden by @WithLanguageLevel annotation
   * Otherwise, the latest language level is used.
   */
  @Test
  @WithLanguageLevel(LanguageLevel.PYTHON38)
  fun withLanguageLevel() {
    codeInsightFixture.doTestByText(
      """
      x: int = <warning descr="Expected type 'int', got 'str' instead">"123"</warning>
      """.trimIndent()
    )
    Assertions.assertEquals(LanguageLevel.forElement(codeInsightFixture.file), LanguageLevel.PYTHON38)
  }

  /**
   * Folder tests can be used to test multiple files in a directory.
   * In this case, all the tests in the folder will be treated as a single-file test.
   */
  @FolderTest
  fun folderTest(file: PsiFile) {
    codeInsightFixture.doTestByFile(file)
  }

  /* Some examples of parameterized tests with JUnit 5 */

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