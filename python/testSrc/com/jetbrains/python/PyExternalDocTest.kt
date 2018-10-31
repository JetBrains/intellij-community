// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.stdlib.PyStdlibDocumentationLinkProvider
import com.jetbrains.python.fixtures.PyTestCase
import junit.framework.TestCase

abstract class PyExternalDocTest : PyTestCase() {
  fun doTest(text: String, expectedUrl: String) {
    val pair = configureByText(text)

    val originalElement = pair.first
    var element: PsiElement? = pair.second

    TestCase.assertEquals(expectedUrl, getDocUrl(element!!, originalElement!!))
  }

  fun configureByText(text: String): Pair<PsiElement?, PsiElement?> {
    myFixture.configureByText(getTestName(false) + ".py", text)

    val originalElement = myFixture.file.findElementAt(myFixture.caretOffset)

    var element: PsiElement?
    val ref = myFixture.getReferenceAtCaretPosition()
    if (ref != null) {
      element = ref.resolve()

      if (element == null) {
        element = ref.element
      }
    }
    else {
      element = originalElement
    }
    return Pair(originalElement, element)
  }

  fun doQuickDocTest(text: String, expectedHtml: String) {
    val pair = configureByText(text)

    val originalElement = pair.first
    var element: PsiElement? = pair.second


    val provider = DocumentationManager.getProviderFromElement(element)
    var urls = provider.getUrlFor(element, originalElement)

    urls = listOf(urls!![0].replace("3.7 Mock SDK", "3.7"))

    TestCase.assertTrue(
      (provider as CompositeDocumentationProvider).fetchExternalDocumentation(myFixture.project, element, urls)!!.contains(expectedHtml))
  }

  private fun getDocUrl(element: PsiElement, originalElement: PsiElement): String? {
    val provider = DocumentationManager.getProviderFromElement(element)

    val urls = provider.getUrlFor(element, originalElement)

    TestCase.assertEquals(1, urls!!.size)
    return urls[0]
  }

  fun doUrl(qname: String, expectedUrl: String) {
    val ind = qname.lastIndexOf(".")
    val moduleName = qname.substring(0, ind)
    val elementName = qname.substring(ind+1)
    val p = PyStdlibDocumentationLinkProvider()
    TestCase.assertEquals(expectedUrl,
                          "${p.webPageName(QualifiedName.fromDottedString(moduleName), projectDescriptor.sdk)}.html#${p.fragmentName(
                            moduleName)}.$elementName")
  }

  override fun getProjectDescriptor() = ourPy3Descriptor
}

class PyExternalDocTestPy3 : PyExternalDocTest() {
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
           "print(os.path.is<caret>link)", "https://docs.python.org/3.7 Mock SDK/library/os.path.html#os.path.islink")

    doTest("import os\n" +
           "print(os.path.isfil<caret>e)", "https://docs.python.org/3.7 Mock SDK/library/os.path.html#os.path.isfile")
  }

  fun testOsPathQuickDoc() { // PY-31223
    doQuickDocTest("""
import os

print(os.path.islink)
print(os.path.isf<caret>ile)
    """.trimIndent(), """<dt id="os.path.isfile">""".trimIndent())
  }

  fun testUrls() {

  }

}


class PyExternalDocTestPy2 : PyExternalDocTest() {
  private val pythonDocsLibrary = "https://docs.python.org/2.7 Mock SDK/library"

  fun testCPickle() {
    doTest("import cPick<caret>le", "$pythonDocsLibrary/pickle.html")
  }

  fun testCPickleDump() {
    doTest("import cPickle\n" +
           "cPickle.du<caret>mp()", "$pythonDocsLibrary/pickle.html#pickle.dump")

  }

  fun testUrls() {
    doUrl("cPickle.dump", "pickle.html#pickle.dump")
    doUrl("pyexpat.ErrorString", "pyexpat.html#xml.parsers.expat.ErrorString")
  }

  override fun getProjectDescriptor() = ourPyDescriptor
}