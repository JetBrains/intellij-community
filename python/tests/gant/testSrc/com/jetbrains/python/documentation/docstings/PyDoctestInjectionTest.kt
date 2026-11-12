// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstings

import com.intellij.idea.TestFor
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.restructuredtext.psi.RestLine
import com.intellij.restructuredtext.python.PyRestDocstringLanguageInjector
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageInjector
import com.jetbrains.python.documentation.doctest.PyDoctestLanguageDialect
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFunction

@Subsystems.QuickDocumentation
@Components.Docstrings
@Layers.Functional
class PyDoctestInjectionTest : PyTestCase() {

  @TestFor(issues = ["PY-94085"])
  fun `test docstring example with prompted backslash continuations is injected once`() {
    val texts = docstringDoctestTexts(
      """
        def f():
            '''
            >>> styled = df.style.\
            ...     applymap(lambda val: 'red' if val < 0 else 'black').\
            ...     highlight_max()
            >>> styled.to_excel('styled.xlsx')
            '''
      """.trimIndent()
    )
    assertEquals(
      listOf(
        """
          >>> styled = df.style.\
              ...     applymap(lambda val: 'red' if val < 0 else 'black').\
              ...     highlight_max()
              >>> styled.to_excel('styled.xlsx')
        """.trimIndent()
      ),
      texts.map { it.trimStart() }
    )
  }

  fun `test docstring example with bare backslash continuations is injected once`() {
    val texts = docstringDoctestTexts(
      """
        def f():
            '''
            >>> x = 1 + \
            2 + \
            3
            >>> x
            6
            '''
      """.trimIndent()
    )
    assertEquals(
      listOf(
        """
          >>> x = 1 + \
              2 + \
              3
              >>> x
        """.trimIndent()
      ),
      texts.map { it.trimStart() }
    )
  }

  @TestFor(issues = ["PY-94085"])
  fun `test rest example with prompted backslash continuations is injected once`() {
    myFixture.configureByText(
      "a.rst",
      """
        Title
        =====

        .. code-block:: pycon

           >>> styled = df.style.\
           ...     applymap(lambda val: 'red' if val < 0 else 'black').\
           ...     highlight_max()
           >>> styled.to_excel('styled.xlsx')

        After.
      """.trimIndent()
    )
    val texts = PsiTreeUtil.findChildrenOfType(myFixture.file, RestLine::class.java)
      .flatMap { doctestTexts(PyRestDocstringLanguageInjector(), it) }
    assertEquals(
      listOf(
        """
          >>> styled = df.style.\
             ...     applymap(lambda val: 'red' if val < 0 else 'black').\
             ...     highlight_max()
             >>> styled.to_excel('styled.xlsx')
        """.trimIndent()
      ),
      texts.map { it.trimStart() }
    )
  }

  private fun docstringDoctestTexts(text: String): List<String> {
    myFixture.configureByText("a.py", text)
    val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
    return doctestTexts(PyDocstringLanguageInjector(), function.docStringExpression!!)
  }

  private fun doctestTexts(injector: LanguageInjector, host: PsiLanguageInjectionHost): List<String> {
    val texts = mutableListOf<String>()
    val places = InjectedLanguagePlaces { language, range, _, _ ->
      if (language == PyDoctestLanguageDialect.getInstance()) {
        texts.add(range.substring(host.text))
      }
    }
    injector.getLanguagesToInject(host, places)
    return texts
  }
}
