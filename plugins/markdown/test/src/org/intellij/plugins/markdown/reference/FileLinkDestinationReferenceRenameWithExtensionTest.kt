package org.intellij.plugins.markdown.reference

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler
import org.intellij.plugins.markdown.MarkdownTestingUtil
import java.nio.file.Path

class FileLinkDestinationReferenceWithExtensionTest : BaseLinkDestinationReferenceTestCase() {

  override fun getTestDataPath() = Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination", "withExtension").toString()

  override fun getLinksFilePath(): String = Path.of("topDir", "links.md").toString()

  fun testRenameWithFullExtension() = testRenameFile(Path.of("stub_in_root.markdown"), "renamed.markdown")

  fun testRenameWithDefaultExtension() = testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  @TestFor(issues = ["IJPL-172056"])
  fun testRenameInFolderWithUnderscore() =
    testRenameFile(Path.of("underscore_", "stub_in_underscore_dir.md"), "renamed.md")

  @TestFor(issues = ["IJPL-172056"])
  fun testMoveOutOfFolderWithUnderscore() {
    myFixture.configureByFile("moveOutOfFolderWithUnderscore.md")
    val target = PsiUtilCore.findFileSystemItem(project, myFixture.findFileInTempDir("underscore_/moved_stub.md"))!!
    val targetDirectory = runWriteActionAndWait { myFixture.file.containingDirectory.createSubdirectory("moved") }
    MoveFilesOrDirectoriesHandler().doMove(project, arrayOf<PsiElement>(target), targetDirectory, null)
    myFixture.checkResultByFile("moveOutOfFolderWithUnderscoreAfter.md")
  }

  fun testRenameWithSpaceAndParentDirectory() {
    myFixture.configureByFile("source/renameWithSpace.md")
    val target = PsiUtilCore.findFileSystemItem(project, myFixture.findFileInTempDir("test2/test data.md"))!!
    assertTrue(ReferencesSearch.search(target).findAll().isNotEmpty())
    myFixture.renameElement(target, "test data2.md")
    myFixture.checkResultByFile("source/renameWithSpaceAfter.md")
  }

  fun testRenameWithNonCanonicalEncoding() {
    myFixture.configureByFile("source/renameWithNonCanonicalEncoding.md")
    val target = PsiUtilCore.findFileSystemItem(project, myFixture.findFileInTempDir("test2/foo+bar.md"))!!
    assertTrue(ReferencesSearch.search(target).findAll().isNotEmpty())
    myFixture.renameElement(target, "foo+baz.md")
    myFixture.checkResultByFile("source/renameWithNonCanonicalEncodingAfter.md")
  }

  fun testNear() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testBelow() = testIsReferenceToFile("topDir", "innerDir", "stub_in_inner_dir.md")

  fun testAbove() = testIsReferenceToFile("stub_in_root.md")

  fun testWithHeader() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testWithNonexistentHeader() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testIsNotReferenceBecauseOfWrongPathPrefix() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testIsNotReferenceBecauseResolvingIsRelative() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtHeader() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeader() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

}
