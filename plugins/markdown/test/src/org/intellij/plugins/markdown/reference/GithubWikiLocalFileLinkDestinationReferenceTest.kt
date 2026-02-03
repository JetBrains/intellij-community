// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import org.intellij.plugins.markdown.MarkdownTestingUtil
import java.nio.file.Path

class GithubWikiLocalFileLinkDestinationReferenceTest : BaseLinkDestinationReferenceTestCase() {
  override fun getTestDataPath() = Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination", "githubwiki").toString()

  override fun getLinksFilePath(): String = Path.of("topDir", "links.md").toString()

  fun testInRoot() = testIsReferenceToFile("stub_in_root.md")

  fun testInDir() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testDirAtTheEnd() = testIsReferenceToFile("topDir")

  fun testDirInTheMiddle() = testIsReferenceToFile("topDir", "innerDir")

  fun testDirInTheMiddleRegardlessTheRest() = testIsReferenceToFile("topDir")

  fun testWithHeader() = testIsReferenceToFile("stub_in_root.md")

  fun testWithNonexistentHeader() = testIsReferenceToFile("stub_in_root.md")

  fun testHeader() = testIsReferenceToHeader(Path.of("stub_in_root.md"), "header")

  fun testInRootWithMissingExtension() = testIsReferenceToFile("stub_in_root.md")

  fun testInDirWithMissingExtension() = testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  fun testRepeatedExtensionWithMissingExtension() = testIsReferenceToFile("topDir", "stub_with_repeated_extension.md.md")

  fun testWithHeaderWithMissingExtension() = testIsReferenceToFile("stub_in_root.md")

  fun testWithNonexistentHeaderWithMissingExtension() = testIsReferenceToFile("stub_in_root.md")

  fun testHeaderWithMissingExtension() = testIsReferenceToHeader(Path.of("stub_in_root.md"), "header")

  fun testIsNotReferenceBecauseOfWrongPathPrefix() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceBecauseOfFullMatchWithOtherFile() = testIsNotReferenceToFile("topDir", "stub_in_top_dir.md.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtHeader() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeader() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceBecauseOfWrongPathPrefixWithMissingExtension() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtHeaderWithMissingExtension() = testIsNotReferenceToFile("stub_in_root.md")

  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeaderWithMissingExtension() = testIsNotReferenceToFile("stub_in_root.md")

  fun testRenameWithMissingExtension() = testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  fun testRenameWithExtension() = testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  fun testRenameDirectory() = testRenameFile(Path.of("topDir", "innerDir"), "renamed")

  fun testRenameNotRenamedBecauseOfFullMatchWithOtherFile() = testRenameFile(Path.of("topDir", "stub_in_top_dir.md.md"), "renamed")

  fun testRenameRepeatedExtensionWithMissingExtension() = testRenameFile(Path.of("topDir", "stub_with_repeated_extension.md.md"),
                                                                         "renamed.md.md")

  fun testRenameExtensionRemovalWithMissingExtension() = testRenameFile(Path.of("stub_in_root.md"), "renamed")

  fun testRenameExtensionIntroduction() = testRenameFile(Path.of("stub_without_extension"), "renamed.md")
}