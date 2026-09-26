package org.intellij.plugins.markdown.reference

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

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

  fun testDefaultEditorHighlighting() {
    doEditorHighlightingTest()
  }

  fun testCustomEditorHighlighting() {
    doEditorHighlightingTest(CodeInsightColors.WARNINGS_ATTRIBUTES)
  }

  private fun doTest(fileName: String) {
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    myFixture.testHighlighting(true, false, false, fileName)
  }

  private fun doEditorHighlightingTest(editorAttributes: TextAttributesKey? = null) {
    val inspection = MarkdownUnresolvedFileReferenceInspection()
    myFixture.enableInspections(inspection)
    if (editorAttributes != null) {
      val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
      profile.setEditorAttributesKey(inspection.shortName, editorAttributes.externalName, null, project)
    }
    myFixture.configureByText("test.md", "[ref]: missing.md")
    val highlight = myFixture.doHighlighting().single { it.severity == HighlightSeverity.WARNING }
    assertEquals(editorAttributes ?: CodeInsightColors.WEAK_WARNING_ATTRIBUTES, highlight.forcedTextAttributesKey)
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(MarkdownUnresolvedFileReferenceInspection())
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}