// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome
import com.intellij.idea.TestFor
import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyKeywordArgument
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91867"], classes = [PyTargetElementEvaluator::class])
@Subsystems.CodeInsight
@Components.Navigation
@Layers.Functional
class PyKeywordArgumentNavigationTest : PyCodeInsightTestCase() {
  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  fun `keyword argument for a parameter is not a declaration`() = runInEdtAndWait {
    val namedElement = namedElementAtCaret("""
      def f(alpha: int): ...

      f(alpha=1)
      # └ CARET
    """)
    assertNull(namedElement)
  }

  @Test
  fun `keyword argument for kwargs declares a name`() = runInEdtAndWait {
    val namedElement = namedElementAtCaret("""
      def f(**kwargs): ...

      f(alpha=1)
      # └ CARET
    """)
    assertEquals("alpha", (namedElement as? PyKeywordArgument)?.keyword)
  }

  @Test
  fun `keyword argument of a dict call declares a key`() = runInEdtAndWait {
    val namedElement = namedElementAtCaret("""
      d = dict(alpha=1)
      #        └ CARET
    """)
    assertEquals("alpha", (namedElement as? PyKeywordArgument)?.keyword)
  }

  @Test
  fun `go to declaration or usages navigates from a keyword argument to the parameter`() = runInEdtAndWait {
    configureWithCaret("""
      def f(alpha: int): ...

      f(alpha=1)
      # └ CARET
    """)
    assertEquals(GTDUOutcome.GTD, gotoDeclarationOrUsagesOutcome())
  }

  /**
   * An LSP server that answers `textDocument/definition` with a plain `Location` gives an implicit reference
   * with an empty range at the caret. The test adds such a reference and checks that the action navigates
   * instead of showing the usages of the keyword argument.
   */
  @Test
  fun `go to declaration or usages navigates from a keyword argument when an LSP server answers without an origin range`() = runInEdtAndWait {
    myFixture.addFileToProject("probe_module.py", """
      def probe(alpha: int = 1, beta: str = "x") -> None: ...
    """.trimIndent())
    configureWithCaret("""
      from probe_module import probe

      probe(alpha=4)
      #       └ CARET
    """)
    ImplicitReferenceProvider.EP_NAME.point.registerExtension(EmptyRangeAtCaretReferenceProvider(myFixture.caretOffset),
                                                              LoadingOrder.FIRST, testDisposable)
    assertEquals(GTDUOutcome.GTD, gotoDeclarationOrUsagesOutcome())
  }

  private fun namedElementAtCaret(@Language("Python") code: String): PsiElement? {
    configureWithCaret(code)
    return TargetElementUtil.getNamedElement(myFixture.file.findElementAt(myFixture.caretOffset))
  }

  private fun gotoDeclarationOrUsagesOutcome(): GTDUOutcome? =
    GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, myFixture.caretOffset)

  private class EmptyRangeAtCaretReferenceProvider(private val offset: Int) : ImplicitReferenceProvider {
    override fun getImplicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference? {
      val file = element as? PsiFile ?: return null
      if (offsetInElement != offset) return null
      return object : PsiSymbolReference {
        override fun getElement(): PsiElement = file
        override fun getRangeInElement(): TextRange = TextRange(offset, offset)
        override fun resolveReference(): Collection<Symbol> = listOf(PsiSymbolService.getInstance().asSymbol(file))
      }
    }
  }
}
