package org.intellij.plugins.markdown.reference

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/reference/linkDestination")
class UnresolvedLinkDestinationReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testUnresolvedReference() {
    doTest("sample_unresolved.md")
  }

  @Test
  fun testGithubWikiResolvedReference() {
    doTest("sample_github_wiki_resolved.md")
  }

  @Test
  fun testGithubWikiResolvedMissingExtensionReference() {
    doTest("sample_github_wiki_missing_extension_resolved.md")
  }

  @Test
  fun testGithubWikiUnresolvedReferenceNotHighlighted() {
    doTest("sample_github_wiki_unresolved.md")
  }

  @Test
  fun testGithubWikiUnresolvedMissingExtensionReferenceNotHighlighted() {
    doTest("sample_github_wiki_missing_extension_unresolved.md")
  }

  @Test
  fun testDefaultEditorHighlighting() {
    doEditorHighlightingTest()
  }

  @Test
  fun testCustomEditorHighlighting() {
    doEditorHighlightingTest(CodeInsightColors.WARNINGS_ATTRIBUTES)
  }

  @Test
  fun testImageResolvesFromProjectRoot() {
    val image = myFixture.addFileToProject("imgs/A.png", "")
    configureInSubdirectory("![image](imgs/A.<caret>png)")
    assertResolvesTo(image)
  }

  @Test
  fun testImageDirectoryResolvesFromProjectRoot() {
    val image = myFixture.addFileToProject("imgs/A.png", "")
    configureInSubdirectory("![image](im<caret>gs/A.png)")
    assertResolvesTo(timeoutRunBlocking { readAction { image.parent!! } })
  }

  @Test
  fun testImageResolvesToDocumentDirectoryFirst() {
    val far = myFixture.addFileToProject("imgs/A.png", "")
    val near = myFixture.addFileToProject("markdowns/imgs/A.png", "")
    configureInSubdirectory("![image](imgs/A.<caret>png)")
    timeoutRunBlocking {
      readAction {
        val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) as PsiPolyVariantReference
        val targets = reference.multiResolve(false).map { it.element }
        assertEquals(2, targets.size)
        assertTrue(myFixture.psiManager.areElementsEquivalent(near, targets[0]))
        assertTrue(myFixture.psiManager.areElementsEquivalent(far, targets[1]))
      }
    }
  }

  @Test
  fun testImageFromProjectRootNotHighlighted() {
    myFixture.addFileToProject("imgs/A.png", "")
    doSubdirectoryTest("![image](imgs/A.png)")
  }

  @Test
  fun testImageInBothDirectoriesNotHighlighted() {
    myFixture.addFileToProject("imgs/A.png", "")
    myFixture.addFileToProject("markdowns/imgs/A.png", "")
    doSubdirectoryTest("![image](imgs/A.png)")
  }

  @Test
  fun testMissingImageHighlighted() {
    doSubdirectoryTest(
      "![image](<warning descr=\"Cannot resolve directory 'imgs'\">imgs</warning>/<warning descr=\"Cannot resolve file 'A.png'\">A.png</warning>)"
    )
  }

  @Test
  fun testAbsoluteFileWithoutExtensionNotHighlighted() {
    myFixture.addFileToProject("topDir/target.md", "")
    doSubdirectoryTest("[target](/topDir/target)")
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
    timeoutRunBlocking {
      readAction {
        val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
        assertNotNull(reference)
        assertTrue(myFixture.psiManager.areElementsEquivalent(target, reference!!.resolve()))
      }
    }
  }

  private fun doEditorHighlightingTest(editorAttributes: TextAttributesKey? = null) {
    val inspection = MarkdownUnresolvedFileReferenceInspection()
    myFixture.enableInspections(inspection)
    if (editorAttributes != null) {
      val profile = InspectionProjectProfileManager.getInstance(myFixture.project).currentProfile
      profile.setEditorAttributesKey(inspection.shortName, editorAttributes.externalName, null, myFixture.project)
    }
    myFixture.configureByText("test.md", "[ref]: missing.md")
    val highlight = myFixture.doHighlighting().single { it.severity == HighlightSeverity.WARNING }
    assertEquals(editorAttributes ?: CodeInsightColors.WEAK_WARNING_ATTRIBUTES, highlight.forcedTextAttributesKey)
  }

  @AfterEach
  fun tearDown() {
    myFixture.disableInspections(MarkdownUnresolvedFileReferenceInspection())
  }
}