package org.intellij.plugins.markdown.model

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.model.psi.headers.UnresolvedHeaderReferenceInspection
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Files

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/inspection")
class UnresolvedHeaderReferenceInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var testName: String

  @Test
  fun `test header in single file is unresolved`(): Unit = doTest()

  @Test
  fun `test header in single file is resolved`(): Unit = doTest()

  @Test
  fun `test headers in project are resolved`(): Unit = doTest()

  @Test
  fun `test headers in project are resolved without file extensions`(): Unit = doTest()

  @Test
  fun `test headers in project are unresolved`(): Unit = doTest()

  @Test
  fun `test anchors in web links are ignored`(): Unit = doTest()

  @Test
  fun `test anchors in non markdown file links are ignored`(): Unit = doTest()

  @Test
  fun `test github line fragments in non markdown file links are ignored`(): Unit = doTest()

  @Test
  fun `test header with uppercase anchor is resolved`(): Unit = doTest()

  @Test
  fun `test unresolved header warning is visible when link destination is folded`() {
    myFixture.enableInspections(UnresolvedHeaderReferenceInspection())
    myFixture.configureByText("unresolved_anchor.md", "[some link](#unresolved-header)")

    val warning = myFixture.doHighlighting().single { it.severity == HighlightSeverity.WARNING }
    assertTrue(warning.highlighter.isVisibleIfFolded, "The warning must stay visible when the link destination is folded")
  }

  @Test
  fun `test header in saved file outside project is resolved`() {
    val text = "# First heading\n\n[First](#first-heading)"
    val externalFile = Files.createTempFile("markdown-anchor", ".md")
    Disposer.register(disposable) { Files.deleteIfExists(externalFile) }
    Files.writeString(externalFile, text)
    val virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(externalFile.toString())!!
    assertFalse(ProjectFileIndex.getInstance(myFixture.project).isInContent(virtualFile))
    myFixture.configureFromExistingVirtualFile(virtualFile)
    assertTrue(Files.readString(externalFile) == text, "The file on disk must contain the test text")

    // The fixture skips daemon inspections for files outside content roots. Run the inspection on the saved file.
    val inspection = LocalInspectionToolWrapper(UnresolvedHeaderReferenceInspection())
    val context = InspectionManager.getInstance(myFixture.project).createNewGlobalContext()
    assertThat(timeoutRunBlocking { readAction { InspectionEngine.runInspectionOnFile(myFixture.file, inspection, context) } }).isEmpty()
  }

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
    myFixture.copyDirectoryToProject("", "")
  }

  private fun doTest() {
    val name = testName
    myFixture.enableInspections(UnresolvedHeaderReferenceInspection())
    myFixture.configureByFile("$name.md")
    myFixture.testHighlighting(true, false, true)
  }
}
