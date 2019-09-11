package org.intellij.plugins.markdown.reference

import com.intellij.openapi.paths.PsiDynaReference
import com.intellij.testFramework.JavaResolveTestCase
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl
import org.intellij.plugins.markdown.lang.references.MarkdownAnchorReference

class HeaderResolveTest : JavaResolveTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/reference/linkDestination/headers/"

  private fun doTest() {
    val fileName = getTestName(true) + ".md"
    val reference = configureByFile(fileName)
    val resolve = reference.resolve()

    assertNotNull(resolve)
    assertTrue(resolve is MarkdownHeaderImpl)
  }

  fun testHeader1() {
    doTest()
  }

  fun testHeader2() {
    doTest()
  }

  fun testInBullet() {
    doTest()
  }

  fun testGfmLinkPunctuation() {
    doTest()
  }

  fun testGfmLinkSpecial() {
    doTest()
  }

  fun testGfmLinkUppercase() {
    doTest()
  }

  fun testAFileHeader1() {
    configureByFile("header1.md")
    doTest()
  }

  fun testAFileHeaderMultipleResolve() {
    configureByFile("multipleHeaders.md")
    checkMultiResolve(2)
  }

  fun testMultipleHeaders() {
    checkMultiResolve(2)
  }

  private fun checkMultiResolve(resolveCount: Int) {
    val fileName = getTestName(true) + ".md"
    val reference = configureByFile(fileName)
    val markdownAnchorReference : MarkdownAnchorReference = ContainerUtil.findInstance((reference as PsiDynaReference<*>).references,
                                                             MarkdownAnchorReference::class.java)

    TestCase.assertNotNull(markdownAnchorReference)
    val result = markdownAnchorReference.multiResolve(false)

    assertTrue(result.size == resolveCount)
  }
}