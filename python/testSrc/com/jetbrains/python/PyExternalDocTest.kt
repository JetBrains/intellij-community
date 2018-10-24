// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.fixtures.PyTestCase
import junit.framework.TestCase

class PyExternalDocTest : PyTestCase() {

  private val pythonDocsLibrary = "https://docs.python.org/3.4 Mock SDK/library"

  fun testBuiltins() { // PY-9061
    val pythonBuiltinsHelp = "$pythonDocsLibrary/functions.html"
    doTest("x = su<caret>m([1, 2, 3,])", "${pythonBuiltinsHelp}#sum")
    doTest("f = ope<caret>n())", "${pythonBuiltinsHelp}#open")
  }

  fun testUnittestMock() { // PY-29887

    doTest("from unittest.mock import Moc<caret>k", "$pythonDocsLibrary/unittest.mock.html#unittest.mock.Mock")
  }

  private fun doTest(text: String, expectedUrl: String) {
    myFixture.configureByText(getTestName(false) + ".py", text)

    TestCase.assertEquals(expectedUrl, getDocUrl(myFixture.elementAtCaret))
  }

  private fun getDocUrl(element: PsiElement): String? {
    val provider = DocumentationManager.getProviderFromElement(element)

    val urls = provider.getUrlFor(element, element)

    TestCase.assertEquals(1, urls!!.size)
    return urls[0]
  }

  override fun getProjectDescriptor() = PyTestCase.ourPy3Descriptor
}