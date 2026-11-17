// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** Checks the DevKit support of the `PyCodeInsightTestCase` assertion language in a Python text that a Java test injects. */
@TestFor(classes = [
  PyTestAssertionAnnotator::class,
  PyTestAssertionCompletionContributor::class,
  PyTestAssertionDocumentationTargetProvider::class,
  PyTestAssertionMarkerHighlightFactory::class,
  PyTestCaseText::class,
])
@TestApplication
class PyTestAssertionEditorTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val pyTestAssertion by pyTestAssertionEditorFixture(projectFixture)
  private val myFixture get() = pyTestAssertion.fixture

  @BeforeEach
  fun addTestCaseStub() {
    myFixture.addClass("""
      package com.jetbrains.python.fixtures;
      import org.intellij.lang.annotations.Language;
      import com.intellij.python.testAssertions.PyTestAssertionText;
      public abstract class PyCodeInsightTestCase {
        protected void test(@PyTestAssertionText @Language("Python") String fileContent) {}
        protected void testQuickFix(@Language("Python") String fileContent) {}
      }
    """.trimIndent())
  }

  @Test
  fun `test misspelled directive is an error with a quick fix`(): Unit = timeoutRunBlocking(timeout = 1.minutes) {
    configureTest("x = 1 # WEAK-WARN<caret>ING message")

    val error = pyTestAssertion.errors().single()
    assertEquals("Test assertion: Unknown directive 'WEAK-WARNING'. Did you mean 'WEAK_WARNING'?", error.description)
    assertEquals("WEAK-WARNING", error.text)

    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.findSingleIntention("Replace with 'WEAK_WARNING'"))
      assertEquals(javaTest(testCall("x = 1 # WEAK_WARNING message")), hostText())
    }
  }

  @Test
  fun `test assertions in another form are an error with a quick fix`(): Unit = timeoutRunBlocking(timeout = 1.minutes) {
    configureTest("""
      x = 1
      #   ^<caret> TYPE int
    """)

    val error = pyTestAssertion.errors().single()
    assertEquals("Test assertion: The assertions of this code line are not in the canonical form, so a correct result fails the test",
                 error.description)

    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.findSingleIntention("Write the assertions in the canonical form"))
      assertEquals(javaTest(testCall("x = 1\n#   └ TYPE int")), hostText())
    }
  }

  @Test
  fun `test problem outside a comment is an error`() {
    configureTest("""s = "# WEAK-WARNING x"""")

    assertEquals(listOf("WEAK-WARNING"), pyTestAssertion.errors().map { it.text })
  }

  @Test
  fun `test plain Python text gets no checks`() {
    configureJava("testQuickFix(${textBlock("x = 1 # WEAK-WARNING message")});")

    assertThat(pyTestAssertion.errors()).isEmpty()
  }

  @Test
  fun `test text that a test method passes on gets the checks`() {
    configureJava("check(${textBlock("x = 1 # WEAK-WARNING message")});",
                  """void check(@Language("Python") String text) { test(text); }""")

    assertThat(pyTestAssertion.errors()).hasSize(1)
  }

  @Test
  fun `test marker on a space is a weak warning but a caret on a space is not`() {
    configureTest("""
      x = f( 1)
      #     └ TYPE int
      #     └ CARET
    """)

    val warnings = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).filter { it.severity == HighlightSeverity.WEAK_WARNING }
    assertEquals(listOf("The marker points at a space"), warnings.map { it.description })
  }

  @Test
  fun `test completion of a directive`(): Unit = timeoutRunBlocking(timeout = 1.minutes) {
    configureTest("x = 1 # TY<caret>")

    val items = withContext(Dispatchers.EDT) { myFixture.completeBasic()?.map { it.lookupString }.orEmpty() }
    assertTrue("TYPE" in items, items.toString())
  }

  @Test
  fun `test documentation of a directive with the caret at its end`() {
    configureTest("x = 1 # TYPE<caret> int")

    assertEquals("TYPE", runReadActionBlocking { documentationAtCaret() })
  }

  @Test
  fun `test documentation of a directive with the caret at its start right after a marker`() {
    configureTest("""
      value = make()
      #       ^^^^<caret>TYPE str
    """)

    assertEquals("TYPE", runReadActionBlocking { documentationAtCaret() })
  }

  @Test
  fun `test marker highlights the code that it marks`() {
    configureTest("""
      value = make()
      #       ^^^^<caret> TYPE str
    """)

    runReadActionBlocking {
      val editor = pyTestAssertion.injectedEditor()
      val file = pyTestAssertion.injectedFile(editor)
      val comment = PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), PsiComment::class.java, false)!!
      val handler = PyTestAssertionMarkerHighlightFactory().createHighlightUsagesHandler(editor, file, comment)!!
      handler.computeUsages(handler.targets)
      assertEquals(listOf("^^^^", "make"), handler.readUsages.map { it.substring(file.text) })
    }
  }

  /** Returns the text of the Java file. The fixture file can be the injected Python file after an action. */
  private fun hostText(): String = InjectedLanguageManager.getInstance(myFixture.project).getTopLevelFile(myFixture.file).text

  private fun documentationAtCaret(): String? {
    val editor = pyTestAssertion.injectedEditor()
    val target = PyTestAssertionDocumentationTargetProvider().documentationTargets(pyTestAssertion.injectedFile(editor), editor.caretModel.offset).single()
    return target.computePresentation().presentableText
  }

  private fun configureTest(pythonText: String) {
    configureJava(testCall(pythonText))
  }

  private fun testCall(pythonText: String): String = "test(${textBlock(pythonText)});"

  private fun configureJava(statement: String, members: String = "") {
    myFixture.configureByText("MyTest.java", javaTest(statement, members))
  }

  /** Returns a Java text block with [pythonText], indented for [javaTest]. */
  private fun textBlock(pythonText: String): String {
    val indent = " ".repeat(6)
    val lines = pythonText.trimIndent().lines().joinToString("\n") { "$indent$it" }
    return "\"\"\"\n$lines\n$indent\"\"\""
  }

  private fun javaTest(statement: String, members: String = ""): String = """
    |import org.intellij.lang.annotations.Language;
    |class MyTest extends com.jetbrains.python.fixtures.PyCodeInsightTestCase {
    |  $members
    |  void run() {
    |    $statement
    |  }
    |}
  """.trimMargin()
}
