// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.idea.TestFor
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyTestCase

private const val TQ = "\"\"\""

/**
 * Tests the Sphinx markup exclusion of the tokenizer-based spellchecker.
 *
 * [com.jetbrains.python.spellchecker.PythonSpellcheckerStrategy] uses a tokenizer only when text-level
 * spellchecking is off. The registry key defaults to on, so every test here turns it off. [PySpellCheckerTest]
 * covers the text-level path, which goes through [com.intellij.python.grazie.PythonTextExtractor] instead.
 */
@Subsystems.IDE
@Layers.Functional
@TestFor(issues = ["PY-27635"])
class PySphinxSpellcheckerTest : PyTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("spellchecker.grazie.enabled").setValue(false, testRootDisposable)
    myFixture.enableInspections(GrazieSpellCheckingInspection::class.java)
  }

  fun testRoleInDocstringIsNotATypo() {
    doTest(
      """
      def f():
          $TQ:py:func:`socket.exeption` is markup, but bare <TYPO descr="Typo: In word 'exeption'">exeption</TYPO> is not.$TQ
          pass
      """
    )
  }

  fun testRoleInCommentIsNotATypo() {
    doTest(
      """
      # :py:func:`socket.exeption` is markup, but bare <TYPO descr="Typo: In word 'exeption'">exeption</TYPO> is not.
      x = 1
      """
    )
  }

  fun testNonPythonDomainRoleIsNotATypo() {
    doTest(
      """
      def f():
          $TQ:ref:`instalation-guide` is markup.$TQ
          pass
      """
    )
  }

  /** A markup range of the first part must not shift into the second part, which would give a negative offset. */
  fun testConcatenatedDocstringWithRole() {
    doTest(
      """
      def f():
          $TQ:py:class:`Foo` $TQ ${TQ}and a bare <TYPO descr="Typo: In word 'exeption'">exeption</TYPO>$TQ
          pass
      """
    )
  }

  private fun doTest(@org.intellij.lang.annotations.Language("Python") text: String) {
    myFixture.configureByText("a.py", text.trimIndent())
    myFixture.checkHighlighting(true, false, true)
  }
}
