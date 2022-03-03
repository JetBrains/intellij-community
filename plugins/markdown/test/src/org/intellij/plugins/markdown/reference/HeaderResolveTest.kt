package org.intellij.plugins.markdown.reference

import com.intellij.openapi.paths.PsiDynaReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.references.MarkdownAnchorReference

class HeaderResolveTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/reference/linkDestination/headers/"

  private fun doTest() {
    val fileName = getTestName(true) + ".md"
    val file = myFixture.configureByFile(fileName)
    val reference = file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertNotNull(reference)
    val resolve = reference?.resolve()

    assertNotNull(resolve)
    assertTrue(resolve is MarkdownHeader)
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
    myFixture.configureByFile("header1.md")
    doTest()
  }

  fun testAFileHeaderMultipleResolve() {
    myFixture.configureByFile("multipleHeaders.md")
    checkMultiResolve(2)
  }

  fun testMultipleHeaders() {
    checkMultiResolve(2)
  }

  private fun checkMultiResolve(resolveCount: Int) {
    val fileName = getTestName(true) + ".md"
    myFixture.configureByFile(fileName)
    val ref = myFixture.getReferenceAtCaretPosition()
    val mdRef = ContainerUtil.findInstance((ref as PsiDynaReference<*>).references,
                                           MarkdownAnchorReference::class.java)

    TestCase.assertNotNull(mdRef)
    val result = mdRef.multiResolve(false)

    assertTrue(result.size == resolveCount)
  }
}