// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.type

import com.intellij.idea.TestFor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.documentation.doctest.PyDoctestFile
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * An LSP-backed type engine may only answer for content the language server actually sees. Injected
 * fragments live in a `VirtualFileWindow` over a `DocumentWindow`, and handing those coordinates to the
 * LSP client trips `Lsp4jUtil.getLsp4jPosition`'s "DocumentWindow is not expected here" assertion.
 *
 * [PyLspTypeEngine.isSupportedForResolve] rejects such an element through two independent guards, one for
 * code fragments and one for injected fragments. Each test below states which guard carries its case.
 */
@TestFor(classes = [PyLspTypeEngine::class], issues = ["PY-91410"])
class PyLspTypeEngineInjectionTest : PyCodeInsightTestCase() {

  private object TestTypeEngine : PyLspTypeEngine {
    override val name: String get() = "test"
    override fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean, isUserInitiated: Boolean): Ref<PyType?>? = null
  }

  @Test
  fun `element of a regular file is supported`() = runInEdtAndWait {
    myFixture.configureByText("a.py", "x = 1")
    val target = PsiTreeUtil.findChildOfType(myFixture.file, PyTargetExpression::class.java)!!
    assertTrue(TestTypeEngine.isSupportedForResolve(target))
  }

  /** Only the injection guard rejects this one: a code-block is injected as plain [PythonLanguage]. */
  @Test
  fun `element of an injected code-block is not supported`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      def spam():
          '''
          Doc.

          .. code-block:: python

             x = 1
          '''
    """.trimIndent())

    val injectedFile = injectedDocstringFiles().first { it.language.`is`(PythonLanguage.INSTANCE) }
    assertFalse(injectedFile is PyExpressionCodeFragment, "A code-block is a plain Python file, not a code fragment")

    val target = PsiTreeUtil.findChildOfType(injectedFile, PyTargetExpression::class.java)!!
    assertFalse(TestTypeEngine.isSupportedForResolve(target))
  }

  /**
   * Doctests are covered twice over: [PyDoctestFile] is a [PyExpressionCodeFragment], so the code-fragment guard
   * already rejected them before the injection guard existed. Pinning both properties keeps this test honest about
   * what it proves — drop either guard and the breakage names the one that was holding this case up.
   */
  @Test
  fun `element of an injected doctest is not supported`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      def spam():
          '''
          >>> x = 1
          '''
    """.trimIndent())

    val injectedFile = injectedDocstringFiles().filterIsInstance<PyDoctestFile>().single()
    @Suppress("UNUSED_VARIABLE", "unused")
    val rejectedByTheCodeFragmentGuard: PyExpressionCodeFragment = injectedFile // compile-time, so dropping it breaks the build
    assertTrue(InjectedLanguageManager.getInstance(myFixture.project).isInjectedFragment(injectedFile),
               "Rejected by the injection guard as well")

    val target = PsiTreeUtil.findChildOfType(injectedFile, PyTargetExpression::class.java)!!
    assertFalse(TestTypeEngine.isSupportedForResolve(target))
  }

  private fun injectedDocstringFiles(): List<PsiFile> {
    val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
    val docstring = PsiTreeUtil.findChildOfType(function, PyStringLiteralExpression::class.java)!!
    val injected = InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(docstring)
    assertNotNull(injected, "No injected PSI files in the docstring")
    return injected!!.mapNotNull { it.first as? PsiFile }
  }
}
