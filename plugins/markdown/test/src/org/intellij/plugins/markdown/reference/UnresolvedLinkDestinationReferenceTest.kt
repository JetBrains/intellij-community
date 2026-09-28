package org.intellij.plugins.markdown.reference

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiPolyVariantReference
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

  fun testImageResolvesFromProjectRoot() {
    val image = myFixture.addFileToProject("imgs/A.png", "")
    configureInSubdirectory("![image](imgs/A.<caret>png)")
    assertResolvesTo(image)
  }

  fun testImageDirectoryResolvesFromProjectRoot() {
    val image = myFixture.addFileToProject("imgs/A.png", "")
    configureInSubdirectory("![image](im<caret>gs/A.png)")
    assertResolvesTo(image.parent!!)
  }

  fun testImageResolvesToDocumentDirectoryFirst() {
    val far = myFixture.addFileToProject("imgs/A.png", "")
    val near = myFixture.addFileToProject("markdowns/imgs/A.png", "")
    configureInSubdirectory("![image](imgs/A.<caret>png)")
    val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) as PsiPolyVariantReference
    val targets = reference.multiResolve(false).map { it.element }
    assertEquals(2, targets.size)
    assertTrue(myFixture.psiManager.areElementsEquivalent(near, targets[0]))
    assertTrue(myFixture.psiManager.areElementsEquivalent(far, targets[1]))
  }

  fun testImageFromProjectRootNotHighlighted() {
    myFixture.addFileToProject("imgs/A.png", "")
    doSubdirectoryTest("![image](imgs/A.png)")
  }

  fun testImageInBothDirectoriesNotHighlighted() {
    myFixture.addFileToProject("imgs/A.png", "")
    myFixture.addFileToProject("markdowns/imgs/A.png", "")
    doSubdirectoryTest("![image](imgs/A.png)")
  }

  fun testMissingImageHighlighted() {
    doSubdirectoryTest(
      "![image](<warning descr=\"Cannot resolve directory 'imgs'\">imgs</warning>/<warning descr=\"Cannot resolve file 'A.png'\">A.png</warning>)"
    )
  }

  private fun doTest(fileName: String) {
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    myFixture.testHighlighting(true, false, false, fileName)
  }

  private fun doSubdirectoryTest(text: String) {
    configureInSubdirectory(text)
    myFixture.checkHighlighting(true, false, false)
  }

  /**
   * Puts the document in a subdirectory, so the project root is not the document directory.
   *
   * It also enables the inspection, because [tearDown] disables it.
   */
  private fun configureInSubdirectory(text: String) {
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("markdowns/README.md", text).virtualFile)
  }

  private fun assertResolvesTo(target: PsiFileSystemItem) {
    val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertNotNull(reference)
    assertTrue(myFixture.psiManager.areElementsEquivalent(target, reference!!.resolve()))
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