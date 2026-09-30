package org.intellij.plugins.markdown.model

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.model.psi.headers.UnresolvedHeaderReferenceInspection
import java.nio.file.Files

class UnresolvedHeaderReferenceInspectionTest: BasePlatformTestCase() {
  fun `test header in single file is unresolved`() = doTest()

  fun `test header in single file is resolved`() = doTest()

  fun `test headers in project are resolved`() = doTest()

  fun `test headers in project are resolved without file extensions`() = doTest()

  fun `test headers in project are unresolved`() = doTest()

  fun `test anchors in web links are ignored`() = doTest()

  fun `test anchors in non markdown file links are ignored`() = doTest()

  fun `test github line fragments in non markdown file links are ignored`() = doTest()

  fun `test header with uppercase anchor is resolved`() = doTest()

  fun `test header in saved file outside project is resolved`() {
    val text = "# First heading\n\n[First](#first-heading)"
    val externalFile = Files.createTempFile("markdown-anchor", ".md")
    Disposer.register(testRootDisposable) { Files.deleteIfExists(externalFile) }
    Files.writeString(externalFile, text)
    val virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(externalFile.toString())!!
    assertFalse(ProjectFileIndex.getInstance(project).isInContent(virtualFile))
    myFixture.configureFromExistingVirtualFile(virtualFile)
    assertTrue("The file on disk must contain the test text", Files.readString(externalFile) == text)

    // The fixture skips daemon inspections for files outside content roots. Run the inspection on the saved file.
    val inspection = LocalInspectionToolWrapper(UnresolvedHeaderReferenceInspection())
    val context = InspectionManager.getInstance(project).createNewGlobalContext()
    assertEmpty(InspectionEngine.runInspectionOnFile(myFixture.file, inspection, context))
  }

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  private fun doTest() {
    val name = getTestName(true)
    myFixture.enableInspections(UnresolvedHeaderReferenceInspection())
    myFixture.configureByFile("$name.md")
    myFixture.testHighlighting(true, false, true)
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/inspection/"
  }
}
