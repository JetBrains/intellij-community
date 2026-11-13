// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class MissingExtensionFileLinkDestinationReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val links by linkDestinationReferenceFixture(
    projectFixture,
    Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination", "missingExtension").toString(),
    Path.of("topDir", "links.md").toString(),
  )

  @Test
  fun testNear(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testBelow(): Unit = links.testIsReferenceToFile("topDir", "innerDir", "stub_in_inner_dir.md")

  @Test
  fun testAbove(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testAbsolute(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testRepeatedExtension(): Unit = links.testIsReferenceToFile("topDir", "stub_with_repeated_extension.md.md")

  @Test
  fun testWithHeader(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testWithNonexistentHeader(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testIsNotReferenceBecauseOfWrongPathPrefix(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testIsNotReferenceBecauseResolvingIsRelative(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceBecauseOfFullMatchWithOtherFile(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtHeader(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeader(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testRenameWithoutExtension(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  @Test
  fun testRenameRepeatedExtension(): Unit = links.testRenameFile(Path.of("topDir", "stub_with_repeated_extension.md.md"), "renamed.md.md")

  @Test
  fun testRenameExtensionRemoval(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed")

  // TODO: move this test to CommonLinkDestinationReferenceTest, since it is a common renaming behavior
  @Test
  fun testRenameExtensionIntroduction(): Unit = links.testRenameFile(Path.of("stub_without_extension"), "renamed.md")

  @Test
  fun testRenameExtensionIntroductionTxt(): Unit = links.testRenameFile(Path.of("stub_without_extension"), "renamed.txt")

  @Test
  fun testRenameWithSpaceAndParentDirectory() {
    links.fixture.configureByFile("source/renameWithSpaceAndParentDirectory.md")
    timeoutRunBlocking {
      val target = readAction { PsiUtilCore.findFileSystemItem(links.fixture.project, links.fixture.findFileInTempDir("test2/test data.md"))!! }
      withContext(Dispatchers.EDT) {
        links.fixture.renameElement(target, "test data2.md")
        links.fixture.checkResultByFile("source/renameWithSpaceAndParentDirectoryAfter.md")
      }
    }
  }

  @Test
  fun testRenameWithEncodedParentDirectory() {
    links.fixture.configureByFile("source/renameWithEncodedParentDirectory.md")
    timeoutRunBlocking {
      val target = readAction { PsiUtilCore.findFileSystemItem(links.fixture.project, links.fixture.findFileInTempDir("test dir/test.md"))!! }
      withContext(Dispatchers.EDT) {
        links.fixture.renameElement(target, "test data.md")
        links.fixture.checkResultByFile("source/renameWithEncodedParentDirectoryAfter.md")
      }
    }
  }

  @Test
  fun testRenameDirectory(): Unit = links.testRenameFile(Path.of("topDir", "innerDir"), "renamed")

  @Test
  fun testRenameNotRenamedBecauseOfFullMatchWithOtherFile(): Unit = links.testRenameFile(Path.of("topDir", "stub_in_top_dir.md.md"), "renamed")
}
