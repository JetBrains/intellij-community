// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.idea.TestFor
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyIncorrectDocstringInspection
import com.jetbrains.python.inspections.quickfix.DocstringQuickFix
import com.jetbrains.python.psi.PyFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(classes = [DocstringQuickFix::class])
@Subsystems.QuickFixes
@Components.Docstrings
@Layers.Functional
class DocstringQuickFixTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-92940"])
  @TestInspections(enableInspections = [PyIncorrectDocstringInspection::class])
  fun `the fix keeps its name and is not available after the missing parameter is deleted`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      def f(x, y):
          '''
          :param y: the second value
          '''
    """.trimIndent())
    val name = PyPsiBundle.message("QFIX.docstring.add.parameter", "x")
    withInspections {
      val action = myFixture.getAllQuickFixes().single { it.text == name }.asModCommandAction()!!
      val parameter = findFunction(myFixture.file as PyFile, "f").parameterList.findParameterByName("x")!!

      WriteCommandAction.runWriteCommandAction(myFixture.project) { parameter.delete() }

      assertEquals(name, action.familyName)
      assertNull(action.getPresentation(ActionContext.from(myFixture.editor, myFixture.file)))
    }
    SuggestedRefactoringProvider.getInstance(myFixture.project).reset()
  }
}
