package org.intellij.plugins.markdown.reference

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.references.MarkdownUnresolvedFileReferenceInspection

class UnresolvedLinkDestinationReferenceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/reference/linkDestination/"

  fun testUnresolvedReference() {
    doTest("sample_unresolved.md")
  }

  fun testUnresolvedFileAnchorReference() {
    doTest("sample_file_anchor_unresolved.md")
  }

  fun testUnresolvedFileAnchor1Reference() {
    myFixture.configureByFile("sample.md")
    doTest("sample_file_anchor_unresolved1.md")
  }

  fun testUnresolvedAnchorReference() {
    doTest("sample_anchor_unresolved.md")
  }

  private fun doTest(fileName: String) {
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    myFixture.testHighlighting(true, false, false, fileName)
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(MarkdownUnresolvedFileReferenceInspection())
    }
    finally {
      super.tearDown()
    }
  }
}