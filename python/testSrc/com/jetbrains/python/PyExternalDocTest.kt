// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.codeInsight.stdlib.PyStdlibDocumentationLinkProvider
import com.jetbrains.python.fixtures.PyTestCase
import junit.framework.TestCase



private fun getDocUrl(element: PsiElement, originalElement: PsiElement): String? {
  val provider = DocumentationManager.getProviderFromElement(element)

  val urls = provider.getUrlFor(element, originalElement)

  TestCase.assertEquals(1, urls!!.size)
  return urls[0]
}

private fun configureByText(text: String, fixture: CodeInsightTestFixture): Pair<PsiElement?, PsiElement?> {
  fixture.configureByText("doc_url_test.py", text)

  val originalElement = fixture.file.findElementAt(fixture.caretOffset)

  var element: PsiElement?
  val ref = fixture.getReferenceAtCaretPosition()
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

fun doTestDocumentationUrl(text: String,
                           expectedUrl: String,
                           fixture: CodeInsightTestFixture) {
  val pair = configureByText(text, fixture)

  val originalElement = pair.first
  val element: PsiElement? = pair.second

  TestCase.assertEquals(expectedUrl, getDocUrl(element!!, originalElement!!))
}

abstract class PyExternalDocTest : PyTestCase() {


  fun doQuickDocTest(text: String, expectedHtml: String) {
    val pair = configureByText(text, myFixture)

    val originalElement = pair.first
    val element: PsiElement? = pair.second


    val provider = DocumentationManager.getProviderFromElement(element)
    var urls = provider.getUrlFor(element, originalElement)

    urls = listOf(urls!![0].replace("3.7 Mock SDK", "3.7"))

    TestCase.assertTrue(
      (provider as CompositeDocumentationProvider)
        .fetchExternalDocumentation(myFixture.project, element, urls, false)!!.contains(expectedHtml))
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

  override fun getProjectDescriptor() = ourPyLatestDescriptor
}

class PyExternalDocTestPy3 : PyExternalDocTest() {
  private val pythonDocsLibrary = "https://docs.python.org/3.7 Mock SDK/library"

  fun testBuiltins() { // PY-9061
    val pythonBuiltinsHelp = "$pythonDocsLibrary/functions.html"
    doTestDocumentationUrl("x = su<caret>m([1, 2, 3,])", "${pythonBuiltinsHelp}#sum", myFixture)
    doTestDocumentationUrl("f = ope<caret>n())", "${pythonBuiltinsHelp}#open", myFixture)
  }

  fun testUnittestMock() { // PY-29887
    doTestDocumentationUrl("from unittest.mock import Moc<caret>k", "$pythonDocsLibrary/unittest.mock.html#unittest.mock.Mock",
                           myFixture)
  }

  fun testOsPath() { // PY-31223
    doTestDocumentationUrl("import os.path\n" +
                           "print(os.path.is<caret>link)", "https://docs.python.org/3.7 Mock SDK/library/os.path.html#os.path.islink",
                           myFixture)

    doTestDocumentationUrl("import os\n" +
                           "print(os.path.isfil<caret>e)", "https://docs.python.org/3.7 Mock SDK/library/os.path.html#os.path.isfile",
                           myFixture)
  }

  fun testOsPathQuickDoc() { // PY-31223
    doQuickDocTest("""
import os

print(os.path.islink)
print(os.path.isf<caret>ile)
    """.trimIndent(), """<dt id="os.path.isfile">""".trimIndent())
  }

  fun testNumpyNdarray() {
    myFixture.copyDirectoryToProject("/inspections/PyNumpyType/numpy", "numpy")

    doTestDocumentationUrl("""import numpy
      a = numpy.multiarray.ndarr<caret>ay()
    """.trimMargin(), "https://docs.scipy.org/doc/numpy/reference/generated/numpy.ndarray.html", myFixture)
  }

}


class PyExternalDocTestPy2 : PyExternalDocTest() {
  private val pythonDocsLibrary = "https://docs.python.org/2.7 Mock SDK/library"

  fun testCPickle() {
    doTestDocumentationUrl("import cPick<caret>le", "$pythonDocsLibrary/pickle.html", myFixture)
  }

  fun testCPickleDump() {
    doTestDocumentationUrl("import cPickle\n" +
                           "cPickle.du<caret>mp()", "$pythonDocsLibrary/pickle.html#pickle.dump", myFixture)

  }

  fun testUrls() {
    doUrl("cPickle.dump", "pickle.html#pickle.dump")
    doUrl("pyexpat.ErrorString", "pyexpat.html#xml.parsers.expat.ErrorString")
  }

  override fun getProjectDescriptor() = ourPyDescriptor
}