// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl
import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyPep8Inspection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The pycodestyle results can refer to a line that the document no longer has.
 * This occurs when the document gets shorter after pycodestyle ran.
 */
@Subsystems.Inspections
@Layers.Functional
@TestFor(classes = [Pep8ExternalAnnotator::class], issues = ["PY-49151"])
class Pep8ProblemAfterLastLineTest : PyCodeInsightTestCase() {

  @Test
  fun `a problem on the line after the last line is still reported`() = runInEdtAndWait {
    myFixture.enableInspections(PyPep8Inspection::class.java)
    val psiFile = myFixture.configureByText("a.py", "x = 1\n")
    val lineCount = myFixture.editor.document.lineCount
    val results = Pep8ExternalAnnotator.Results(HighlightDisplayLevel.WEAK_WARNING)
    results.problems.add(Pep8ExternalAnnotator.Problem(lineCount + 1, 1, "E501", "line too long (130 > 120 characters)"))
    val annotator = Pep8ExternalAnnotator()

    val messages = AnnotationSessionImpl.computeWithSession(psiFile, false, annotator) { holder ->
      (holder as AnnotationHolderImpl).applyExternalAnnotatorWithContext(psiFile, results)
      holder.map { it.message }
    }
    assertThat(messages).containsExactly("PEP 8: E501 line too long (130 > 120 characters)")
  }
}
