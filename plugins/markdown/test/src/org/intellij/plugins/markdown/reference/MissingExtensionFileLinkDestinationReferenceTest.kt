// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.MarkdownTestingUtil
import java.nio.file.Path

class MissingExtensionFileLinkDestinationReferenceTest : BaseLinkDestinationReferenceTestCase() {
  override fun getTestDataPath() = Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination",
                                           "missingExtension").toString()

  override fun getLinksFilePath(): String = Path.of("topDir", "links.md").toString()

  fun testNear() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testBelow() = testIsReferenceToFile("topDir", "innerDir", "stub_in_inner_dir.md")

  fun testAbove() = testIsReferenceToFile("stub_in_root.md")

  fun testRepeatedExtension() = testIsReferenceToFile("topDir", "stub_with_repeated_extension.md.md")

  fun testWithHeader() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testWithNonexistentHeader() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testIsNotReferenceBecauseOfWrongPathPrefix() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testIsNotReferenceBecauseResolvingIsRelative() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceBecauseOfFullMatchWithOtherFile() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtHeader() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeader() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testRenameWithoutExtension() = testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  fun testRenameRepeatedExtension() = testRenameFile(Path.of("topDir", "stub_with_repeated_extension.md.md"), "renamed.md.md")

  fun testRenameExtensionRemoval() = testRenameFile(Path.of("stub_in_root.md"), "renamed")

  // TODO: move this test to CommonLinkDestinationReferenceTest, since it is a common renaming behavior
  fun testRenameExtensionIntroduction() = testRenameFile(Path.of("stub_without_extension"), "renamed.md")

  fun testRenameExtensionIntroductionTxt() = testRenameFile(Path.of("stub_without_extension"), "renamed.txt")

  fun testRenameWithSpaceAndParentDirectory() {
    myFixture.configureByFile("source/renameWithSpaceAndParentDirectory.md")
    val target = PsiUtilCore.findFileSystemItem(project, myFixture.findFileInTempDir("test2/test data.md"))!!
    myFixture.renameElement(target, "test data2.md")
    myFixture.checkResultByFile("source/renameWithSpaceAndParentDirectoryAfter.md")
  }

  fun testRenameWithEncodedParentDirectory() {
    myFixture.configureByFile("source/renameWithEncodedParentDirectory.md")
    val target = PsiUtilCore.findFileSystemItem(project, myFixture.findFileInTempDir("test dir/test.md"))!!
    myFixture.renameElement(target, "test data.md")
    myFixture.checkResultByFile("source/renameWithEncodedParentDirectoryAfter.md")
  }

  fun testRenameDirectory() = testRenameFile(Path.of("topDir", "innerDir"), "renamed")

  fun testRenameNotRenamedBecauseOfFullMatchWithOtherFile() = testRenameFile(Path.of("topDir", "stub_in_top_dir.md.md"), "renamed")
}