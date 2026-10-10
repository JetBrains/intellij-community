// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * Checks which Python strings of a Kotlin test get the DevKit support of the `PyCodeInsightTestCase` assertion language.
 *
 * The tests of the monorepo are Kotlin tests. So these cases cover the call forms that such tests use.
 */
@TestFor(classes = [PyTestCaseText::class])
class PyTestAssertionKotlinEditorTest : PyTestAssertionEditorTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

  override fun setUp() {
    super.setUp()
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

  fun `test string argument`() {
    configureKotlin("test($TEXT)")

    assertSize(1, errors())
  }

  fun `test named argument with trimIndent`() {
    configureKotlin("test(fileContent = $TEXT.trimIndent())")

    assertSize(1, errors())
  }

  fun `test string that a member function passes on`() {
    configureKotlin("check($TEXT)", """private fun check(@Language("Python") text: String) = test(text.trimIndent(), "a.py" to "")""")

    assertSize(1, errors())
  }

  fun `test plain Python text gets no checks`() {
    configureKotlin("""testQuickFix($TEXT, "Fix")""")

    assertEmpty(errors())
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
    /** A Kotlin raw string with a misspelled directive. */
    const val TEXT = "\"\"\"\n      x = 1 # WEAK-WARNING message\n    \"\"\""
  }
}
