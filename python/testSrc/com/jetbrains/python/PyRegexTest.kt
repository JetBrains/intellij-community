// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.fixtures.PyTestCase
import junit.framework.TestCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 *  tests for the `regex` package
 */
class PyRegexTest : PyTestCase() {
  fun `test avoid class failing because there are no tests`() {
    // TODO: delete this when we refactor to JUnit5
  }

  private fun doTestInjectedText(text: String, expected: String): PsiElement {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    val languageManager = InjectedLanguageManager.getInstance(myFixture.project)
    val host = languageManager.getInjectionHost(elementAtCaret)
    assertNotNull(host)
    val files = languageManager.getInjectedPsiFiles(host!!)
    assertNotNull(files)
    assertFalse(files!!.isEmpty())
    val injected = files[0]!!.first
    TestCase.assertEquals(expected, injected.text)
    return injected
  }

  @ParameterizedTest
  @ValueSource(strings = ["compile", "splititer", "subf", "subfn", "template"])
  @TestFor(issues = ["PY-21499"])
  fun `language injection`(regexFunction: String) {
    runBare {
      runWithAdditionalFileInLibDir("regex.py", "def $regexFunction(): ...") {
        doTestInjectedText(
          """
          import regex
         
          regex.$regexFunction("<caret>.*a")
          """.trimIndent(),
          ".*a"
        )
      }
    }
  }
}
