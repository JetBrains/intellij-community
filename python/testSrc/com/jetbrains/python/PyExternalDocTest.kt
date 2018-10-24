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

  private val pythonDocsLibrary = "https://docs.python.org/3.7 Mock SDK/library"

  fun testBuiltins() { // PY-9061
    val pythonBuiltinsHelp = "$pythonDocsLibrary/functions.html"
    doTest("x = su<caret>m([1, 2, 3,])", "${pythonBuiltinsHelp}#sum")
    doTest("f = ope<caret>n())", "${pythonBuiltinsHelp}#open")
  }

  fun testUnittestMock() { // PY-29887
    doTest("from unittest.mock import Moc<caret>k", "$pythonDocsLibrary/unittest.mock.html#unittest.mock.Mock")
  }

  fun testOsPath() { // PY-31223
    doTest("import os.path\n" +
           "\n" +
           "print(os.path.is<caret>link)", "")

    doTest("import os\n" +
           "print(os.path.isfil<caret>e)", "")
  }

  private fun doTest(text: String, expectedUrl: String) {
    myFixture.configureByText(getTestName(false) + ".py", text)

    val originalElement = myFixture.file.findElementAt(myFixture.caretOffset)

    var element: PsiElement?
    val ref = myFixture.getReferenceAtCaretPosition()
    if (ref != null) {
      element = ref.resolve()

      if (element == null) {
        element = ref.element
      }
    } else {
      element = originalElement
    }


    TestCase.assertEquals(expectedUrl, getDocUrl(element!!, originalElement!!))
  }

  private fun getDocUrl(element: PsiElement, originalElement: PsiElement): String? {
    val provider = DocumentationManager.getProviderFromElement(element)

    val urls = provider.getUrlFor(element, originalElement)

    TestCase.assertEquals(1, urls!!.size)
    return urls[0]
  }

  override fun getProjectDescriptor() = PyTestCase.ourPy3Descriptor
}