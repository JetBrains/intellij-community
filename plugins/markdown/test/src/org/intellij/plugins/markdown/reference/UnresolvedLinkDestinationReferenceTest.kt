package org.intellij.plugins.markdown.reference

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.references.paths.MarkdownUnresolvedFileReferenceInspection

class UnresolvedLinkDestinationReferenceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/reference/linkDestination/"

  fun testUnresolvedReference() {
    doTest("sample_unresolved.md")
  }

  fun testGithubWikiResolvedReference() {
    doTest("sample_github_wiki_resolved.md")
  }

  fun testGithubWikiResolvedMissingExtensionReference() {
    doTest("sample_github_wiki_missing_extension_resolved.md")
  }

  fun testGithubWikiUnresolvedReferenceNotHighlighted() {
    doTest("sample_github_wiki_unresolved.md")
  }

  fun testGithubWikiUnresolvedMissingExtensionReferenceNotHighlighted() {
    doTest("sample_github_wiki_missing_extension_unresolved.md")
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