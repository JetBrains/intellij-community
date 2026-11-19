package org.intellij.plugins.markdown.reference

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class FileLinkDestinationReferenceWithExtensionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val links by linkDestinationReferenceFixture(
    projectFixture,
    Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination", "withExtension").toString(),
    Path.of("topDir", "links.md").toString(),
  )

  @Test
  fun testRenameWithFullExtension(): Unit = links.testRenameFile(Path.of("stub_in_root.markdown"), "renamed.markdown")

  @Test
  fun testRenameWithDefaultExtension(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  @Test
  @TestFor(issues = ["IJPL-172056"])
  fun testRenameInFolderWithUnderscore(): Unit = links.testRenameFile(Path.of("underscore_", "stub_in_underscore_dir.md"), "renamed.md")

  @Test
  @TestFor(issues = ["IJPL-172056"])
  fun testMoveOutOfFolderWithUnderscore() {
    links.fixture.configureByFile("moveOutOfFolderWithUnderscore.md")
    timeoutRunBlocking {
      val target = readAction { PsiUtilCore.findFileSystemItem(links.fixture.project, links.fixture.findFileInTempDir("underscore_/moved_stub.md"))!! }
      val targetDirectory = runWriteActionAndWait { links.fixture.file.containingDirectory.createSubdirectory("moved") }
      withContext(Dispatchers.EDT) {
        MoveFilesOrDirectoriesHandler().doMove(links.fixture.project, arrayOf<PsiElement>(target), targetDirectory, null)
        links.fixture.checkResultByFile("moveOutOfFolderWithUnderscoreAfter.md")
      }
    }
  }

  @Test
  @TestFor(issues = ["IJPL-257043"])
  fun testRenameWithFileNameInLinkText(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  @Test
  @TestFor(issues = ["IJPL-257042"])
  fun testMoveWithFileNameInLinkText() {
    links.fixture.configureByFile("moveWithFileNameInLinkText.md")
    timeoutRunBlocking {
      val target = readAction { PsiUtilCore.findFileSystemItem(links.fixture.project, links.fixture.findFileInTempDir("underscore_/moved_stub.md"))!! }
      val targetDirectory = runWriteActionAndWait { links.fixture.file.containingDirectory.createSubdirectory("moved") }
      withContext(Dispatchers.EDT) {
        MoveFilesOrDirectoriesHandler().doMove(links.fixture.project, arrayOf<PsiElement>(target), targetDirectory, null)
        links.fixture.checkResultByFile("moveWithFileNameInLinkTextAfter.md")
      }
    }
  }

  @Test
  fun testRenameWithSpaceAndParentDirectory() {
    links.fixture.configureByFile("source/renameWithSpace.md")
    timeoutRunBlocking {
      val target = readAction { PsiUtilCore.findFileSystemItem(links.fixture.project, links.fixture.findFileInTempDir("test2/test data.md"))!! }
      assertTrue(runReadActionBlocking { ReferencesSearch.search(target).findAll()}.isNotEmpty())
      withContext(Dispatchers.EDT) {
        links.fixture.renameElement(target, "test data2.md")
        links.fixture.checkResultByFile("source/renameWithSpaceAfter.md")
      }
    }
  }

  @Test
  fun testRenameWithNonCanonicalEncoding() {
    links.fixture.configureByFile("source/renameWithNonCanonicalEncoding.md")
    timeoutRunBlocking {
      val target = readAction { PsiUtilCore.findFileSystemItem(links.fixture.project, links.fixture.findFileInTempDir("test2/foo+bar.md"))!! }
      assertTrue(runReadActionBlocking { ReferencesSearch.search(target).findAll()}.isNotEmpty())
      withContext(Dispatchers.EDT) {
        links.fixture.renameElement(target, "foo+baz.md")
        links.fixture.checkResultByFile("source/renameWithNonCanonicalEncodingAfter.md")
      }
    }
  }

  @Test
  fun testNear(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testBelow(): Unit = links.testIsReferenceToFile("topDir", "innerDir", "stub_in_inner_dir.md")

  @Test
  fun testAbove(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testWithHeader(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testWithNonexistentHeader(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testIsNotReferenceBecauseOfWrongPathPrefix(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testIsNotReferenceBecauseResolvingIsRelative(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtHeader(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeader(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

}
