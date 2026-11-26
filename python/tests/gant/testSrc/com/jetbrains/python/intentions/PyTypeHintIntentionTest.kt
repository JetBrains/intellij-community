// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.intentions

import com.intellij.idea.TestFor
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention
import com.jetbrains.python.codeInsight.intentions.PyAnnotateVariableTypeIntention
import com.jetbrains.python.codeInsight.intentions.PyConvertTypeCommentToVariableAnnotationIntention
import com.jetbrains.python.codeInsight.intentions.SpecifyTypeInPy3AnnotationsIntention
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyMissingTypeHintsInspection
import com.jetbrains.python.psi.PyFile
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-94464"])
@Subsystems.CodeInsight
@Components.Intentions
@Layers.Functional
class PyTypeHintIntentionTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(classes = [SpecifyTypeInPy3AnnotationsIntention::class])
  fun `parameter annotation preview shows the annotation`() = assertPreview(
    """
      def f(x):
      #     └ CARET
          pass
    """,
    PyPsiBundle.message("INTN.specify.type.in.annotation"),
    """
      def f(x: object):
          pass
    """,
  )

  @Test
  @TestFor(classes = [SpecifyTypeInPy3AnnotationsIntention::class])
  fun `return type annotation preview shows the annotation`() = assertPreview(
    """
      def f():
      #   └ CARET
          return 1
    """,
    PyPsiBundle.message("INTN.specify.return.type.in.annotation"),
    """
      def f() -> int:
          return 1
    """,
  )

  @Test
  @TestFor(classes = [SpecifyTypeInPy3AnnotationsIntention::class])
  fun `return type annotation from a call goes to the function in another file`() = runInEdtAndWait {
    val mod = myFixture.addFileToProject("mod.py", """
      def f():
          return 1
    """.trimIndent())
    configureWithCaret("""
      from mod import f

      f()
      #\ CARET
    """)
    val text = myFixture.file.text

    launch(PyPsiBundle.message("INTN.specify.return.type.in.annotation"))

    assertEquals("""
      def f() -> int:
          return 1
    """.trimIndent(), mod.text)
    assertEquals(text, myFixture.editor.document.text)
  }

  @Test
  @TestFor(classes = [PyAnnotateTypesIntention::class])
  fun `function type hints preview shows all annotations`() = assertPreview(
    """
      def f(x, y=1):
      #   └ CARET
          return y
    """,
    PyPsiBundle.message("INTN.add.type.hints.for.function", "f"),
    """
      def f(x: object, y: object = 1) -> int:
          return y
    """,
  )

  @Test
  @TestFor(classes = [PyAnnotateTypesIntention::class, PyMissingTypeHintsInspection::class])
  fun `type hints added in a write command, as the quick fix of the missing type hints inspection does`() = runInEdtAndWait {
    configureWithCaret("""
      def f(x):
      #   └ CARET
          return 1
    """)
    val function = (myFixture.file as PyFile).findTopLevelFunction("f")!!

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      PyAnnotateTypesIntention.annotateTypes(myFixture.editor, function)
    }

    myFixture.checkResult("""
      def f(x: object) -> int:
          return 1
    """.trimIndent(), true)
  }

  @Test
  @TestFor(classes = [PyAnnotateVariableTypeIntention::class])
  fun `variable annotation preview shows the annotation`() = assertPreview(
    """
      x = len("a")
      #\ CARET
    """,
    PyPsiBundle.message("INTN.add.type.hint.for.variable", "x"),
    """
      x: [int] = len("a")
    """,
  )

  @Test
  @TestFor(classes = [PyConvertTypeCommentToVariableAnnotationIntention::class])
  fun `type comment conversion preview shows the annotation`() = assertPreview(
    """
      x = [] # type: list[int]
      #         └ CARET
    """,
    PyPsiBundle.message("INTN.convert.type.comment.to.variable.annotation"),
    // The space before the removed comment stays.
    "x: list[int] = [] ",
  )

  private fun assertPreview(@Language("Python") before: String, intentionText: String, preview: String) =
    runInEdtAndWait {
      configureWithCaret(before)
      val text = myFixture.file.text
      val action = myFixture.findSingleIntention(intentionText)

      assertEquals(preview.trimIndent(), myFixture.getIntentionPreviewText(action))
      assertEquals(text, myFixture.file.text)
    }

  private fun launch(intentionText: String) {
    myFixture.launchAction(myFixture.findSingleIntention(intentionText))
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
  }
}
