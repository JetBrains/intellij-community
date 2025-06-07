package org.intellij.plugins.markdown.reference

import org.intellij.plugins.markdown.MarkdownTestingUtil
import java.nio.file.Path

class FileLinkDestinationReferenceWithExtensionTest : BaseLinkDestinationReferenceTestCase() {

  override fun getTestDataPath() = Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination", "withExtension").toString()

  override fun getLinksFilePath(): String = Path.of("topDir", "links.md").toString()

  fun testRenameWithFullExtension() = testRenameFile(Path.of("stub_in_root.markdown"), "renamed.markdown")

  fun testRenameWithDefaultExtension() = testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

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
