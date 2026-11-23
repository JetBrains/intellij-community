// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Checks which Python strings of a Kotlin test get the DevKit support of the `PyCodeInsightTestCase` assertion language.
 *
 * The tests of the monorepo are Kotlin tests. So these cases cover the call forms that such tests use.
 */
@TestFor(classes = [PyTestCaseText::class])
@TestApplication
class PyTestAssertionKotlinEditorTest {
  private val pyTestAssertion by pyTestAssertionEditorFixture(projectFixture)
  private val myFixture get() = pyTestAssertion.fixture

  @BeforeEach
  fun addTestCaseStub() {
    myFixture.addFileToProject("com/jetbrains/python/fixtures/PyCodeInsightTestCase.kt", """
      package com.jetbrains.python.fixtures
      import org.intellij.lang.annotations.Language
      import com.intellij.python.testAssertions.PyTestAssertionText
      abstract class PyCodeInsightTestCase {
        protected fun test(@PyTestAssertionText @Language("Python") fileContent: String, vararg otherFiles: Pair<String, String>) {}
        protected fun testQuickFix(@Language("Python") fileContent: String, quickFixName: String) {}
      }
    """.trimIndent())
  }

  @Test
  fun `test string argument`() {
    configureKotlin("test($TEXT)")

    assertThat(pyTestAssertion.errors()).hasSize(1)
  }

  @Test
  fun `test named argument with trimIndent`() {
    configureKotlin("test(fileContent = $TEXT.trimIndent())")

    assertThat(pyTestAssertion.errors()).hasSize(1)
  }

  @Test
  fun `test string that a member function passes on`() {
    configureKotlin("check($TEXT)", """private fun check(@Language("Python") text: String) = test(text.trimIndent(), "a.py" to "")""")

    assertThat(pyTestAssertion.errors()).hasSize(1)
  }

  @Test
  fun `test plain Python text gets no checks`() {
    configureKotlin("""testQuickFix($TEXT, "Fix")""")

    assertThat(pyTestAssertion.errors()).isEmpty()
  }

  private fun configureKotlin(statement: String, members: String = "") {
    myFixture.configureByText("MyTest.kt", """
      |import org.intellij.lang.annotations.Language
      |class MyTest : com.jetbrains.python.fixtures.PyCodeInsightTestCase() {
      |  $members
      |  fun run() {
      |    $statement
      |  }
      |}
    """.trimMargin())
  }

  private companion object {
    private val projectFixture = lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance())

    /** A Kotlin raw string with a misspelled directive. */
    const val TEXT = "\"\"\"\n      x = 1 # WEAK-WARNING message\n    \"\"\""
  }
}
