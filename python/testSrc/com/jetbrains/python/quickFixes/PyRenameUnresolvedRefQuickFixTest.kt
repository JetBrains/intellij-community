// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PythonTemplateRunner
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.quickfix.PyRenameUnresolvedRefQuickFix
import com.jetbrains.python.psi.PyReferenceExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(classes = [PyRenameUnresolvedRefQuickFix::class, PythonTemplateRunner::class])
@Subsystems.QuickFixes
@Components.Inspection
@Layers.Functional
class PyRenameUnresolvedRefQuickFixTest : PyCodeInsightTestCase() {
  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  @TestFor(issues = ["PY-92939"])
  fun `the preview of a descriptor in another file changes nothing and starts no template`() = runInEdtAndWait {
    TemplateManagerImpl.setTemplateTesting(testDisposable)
    configureWithCaret("""
      def f(y1):
          return y + y
      #          └ CARET
    """.trimIndent())
    val text = myFixture.file.text
    val reference = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), PyReferenceExpression::class.java)!!
    val descriptor = InspectionManager.getInstance(myFixture.project)
      .createProblemDescriptor(reference, "unresolved", PyRenameUnresolvedRefQuickFix(), ProblemHighlightType.GENERIC_ERROR, true)
    // The preview copy comes from another file, so `QuickFixWrapper` gives the fix the descriptor in the physical file.
    val previewCopy = IntentionPreviewUtils.obtainCopyForPreview(myFixture.addFileToProject("b.py", text))

    IntentionPreviewUtils.previewSession(myFixture.editor) {
      ReadAction.run<Throwable> { QuickFixWrapper.wrap(descriptor, 0).generatePreview(myFixture.project, myFixture.editor, previewCopy) }
    }

    assertEquals(text, myFixture.file.text)
    assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
  }
}
