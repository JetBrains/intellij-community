// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.paths.WebReference
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil.unwrapMultiReference
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.references.paths.github.GithubWikiLocalFileReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class GithubWikiLocalFileLinkDestinationReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val links by linkDestinationReferenceFixture(
    projectFixture,
    Path.of(MarkdownTestingUtil.TEST_DATA_PATH, "reference", "linkDestination", "githubwiki").toString(),
    Path.of("topDir", "links.md").toString(),
  )

  @BeforeEach
  fun setUp() {
    links.fixture.addFileToProject(".git/config", """
      [remote "origin"]
        url = https://github.com/any_username/any_repository.git
      [remote "repository"]
        url = https://github.com/username/repository.git
      [remote "enterprise"]
        url = https://jetbrains.github.com/username/enterprise-repository.git
      [remote "scp"]
        url = git@github.com:username/scp-repository.git
      [remote "ssh"]
        url = ssh://git@github.com/username/ssh-repository.git
    """.trimIndent())
  }

  @Test
  fun testInRoot(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testInDir(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testDirAtTheEnd(): Unit = links.testIsReferenceToFile("topDir")

  @Test
  fun testDirInTheMiddle(): Unit = links.testIsReferenceToFile("topDir", "innerDir")

  @Test
  fun testDirInTheMiddleRegardlessTheRest(): Unit = links.testIsReferenceToFile("topDir")

  @Test
  fun testWithHeader(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testWithNonexistentHeader(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testHeader(): Unit = links.testIsReferenceToHeader(Path.of("stub_in_root.md"), "header")

  @Test
  fun testInRootWithMissingExtension(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testInDirWithMissingExtension(): Unit = links.testIsReferenceToFile("topDir", "stub_in_top_dir.md")

  @Test
  fun testRepeatedExtensionWithMissingExtension(): Unit = links.testIsReferenceToFile("topDir", "stub_with_repeated_extension.md.md")

  @Test
  fun testWithHeaderWithMissingExtension(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testWithNonexistentHeaderWithMissingExtension(): Unit = links.testIsReferenceToFile("stub_in_root.md")

  @Test
  fun testHeaderWithMissingExtension(): Unit = links.testIsReferenceToHeader(Path.of("stub_in_root.md"), "header")

  @Test
  fun testIsNotReferenceBecauseOfWrongPathPrefix(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceBecauseOfFullMatchWithOtherFile(): Unit = links.testIsNotReferenceToFile("topDir", "stub_in_top_dir.md.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtHeader(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeader(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceBecauseOfWrongPathPrefixWithMissingExtension(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtHeaderWithMissingExtension(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testIsNotReferenceToFileBecauseCaretIsAtNonexistentHeaderWithMissingExtension(): Unit = links.testIsNotReferenceToFile("stub_in_root.md")

  @Test
  fun testRenameWithMissingExtension(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  @Test
  fun testRenameWithExtension(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed.md")

  @Test
  fun testRenameDirectory(): Unit = links.testRenameFile(Path.of("topDir", "innerDir"), "renamed")

  @Test
  fun testRenameNotRenamedBecauseOfFullMatchWithOtherFile(): Unit = links.testRenameFile(Path.of("topDir", "stub_in_top_dir.md.md"), "renamed")

  @Test
  fun testRenameRepeatedExtensionWithMissingExtension(): Unit = links.testRenameFile(Path.of("topDir", "stub_with_repeated_extension.md.md"),
                                                                         "renamed.md.md")

  @Test
  fun testRenameExtensionRemovalWithMissingExtension(): Unit = links.testRenameFile(Path.of("stub_in_root.md"), "renamed")

  @Test
  fun testRenameExtensionIntroduction(): Unit = links.testRenameFile(Path.of("stub_without_extension"), "renamed.md")

  @Test
  fun testFullUrlResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  @Test
  fun testFullUrlWithExtensionResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Main.md"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  @Test
  fun testEnterpriseUrlResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://jetbrains.github.com/username/enterprise-repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  @Test
  fun testScpLikeRemotesResolveInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/scp-repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  @Test
  fun testSshRemotesResolveInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/ssh-repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  @Test
  fun testEncodedPageNameResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/test%20data"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "test data.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/test data.md")
  }

  @Test
  fun testDomainLessUrlResolvesInSourceRepository() {
    createRepositoryStructure()
    links.fixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    val link = "/username/repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTargetWithoutHighlighting(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTargetWithoutHighlighting(wikiReferences, "wiki/Main.md")
  }

  @Test
  fun testMarkdownFileWinsDirectoryConflict() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/dir"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "dir.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/dir.md")
  }

  @Test
  fun testMarkdownDirectoryDoesNotResolveAsFile() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Page"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  @Test
  fun testMissingWikiPageUsesWebReference() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Missing"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  @Test
  fun testAnotherRepositoryUsesWebReference() {
    createRepositoryStructure()
    val link = "https://github.com/AnotherOwner/AnotherProject/wiki/Main"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  @Test
  fun testSameRepositoryOnAnotherHostUsesWebReference() {
    createRepositoryStructure()
    val link = "https://another.github.com/username/repository/wiki/Main"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  @Test
  fun testMissingLocalFileRemainsUnresolved() {
    createRepositoryStructure()
    links.fixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    configureMissingLocalFileLink("Links.md")
    assertUnresolvedLocalFile()
    configureMissingLocalFileLink("wiki/Links.md")
    assertUnresolvedLocalFile()
  }

  private fun assertLocalTarget(references: List<PsiReference>, expectedTarget: String): Unit = timeoutRunBlocking {
    readAction {
      val targets = references.filterIsInstance<GithubWikiLocalFileReference>().mapNotNull { it.resolve() }
      val referenceDetails = references.joinToString { "${it.javaClass.simpleName}: ${it.resolve()}" }
      assertEquals(1, targets.size, "The link must have one local target. References: $referenceDetails")
      val targetFile = assertInstanceOf(PsiFileSystemItem::class.java, targets.single()).virtualFile
      assertEquals(links.fixture.findFileInTempDir(expectedTarget), targetFile)
    }
  }

  private fun assertLocalTargetWithoutHighlighting(references: List<PsiReference>, expectedTarget: String) {
    assertLocalTarget(references, expectedTarget)
    val descriptions = links.fixture.doHighlighting().mapNotNull { it.description }
    assertFalse(descriptions.any { it.startsWith("Cannot resolve ") },
                "The resolved link must not have an unresolved path warning: $descriptions")
  }

  private fun assertWebReference(references: List<PsiReference>): Unit = timeoutRunBlocking {
    readAction {
      assertTrue(references.any { it is WebReference }, "The link must keep a web reference")
      assertFalse(references.filterIsInstance<GithubWikiLocalFileReference>().any { it.resolve() != null }, "The link must not have a local target")
    }
  }

  private fun configureLink(source: String, link: String) = with(links.fixture) {
    val sourceFile = addFileToProject(source, "[Link]($link)")
    configureFromExistingVirtualFile(sourceFile.virtualFile)
    timeoutRunBlocking {
      val offset = readAction { file.text.indexOf(link) + link.lastIndex }
      withContext(Dispatchers.EDT) {
        editor.caretModel.moveToOffset(offset)
      }
      readAction { unwrapMultiReference(file.findReferenceAt(offset)!!) }
    }
  }

  private fun configureMissingLocalFileLink(source: String) {
    val sourceFile = links.fixture.addFileToProject(source, "[Missing](Missing.md)")
    links.fixture.configureFromExistingVirtualFile(sourceFile.virtualFile)
  }

  private fun assertUnresolvedLocalFile() {
    val descriptions = links.fixture.doHighlighting().mapNotNull { it.description }
    assertThat(descriptions).contains("Cannot resolve file 'Missing.md'")
  }

  private fun createRepositoryStructure() = with(links.fixture) {
    addFileToProject(".git/modules/wiki/config", """
      [remote "origin"]
        url = https://github.com/username/repository.wiki.git
      [remote "enterprise"]
        url = https://jetbrains.github.com/username/enterprise-repository.wiki.git
      [remote "scp"]
        url = git@github.com:username/scp-repository.wiki.git
      [remote "ssh"]
        url = ssh://git@github.com/username/ssh-repository.wiki.git
    """.trimIndent())
    addFileToProject("Main.md", "Main repository page")
    addFileToProject("test data.md", "Encoded page name")
    addFileToProject("dir/placeholder.txt", "Directory conflict")
    addFileToProject("dir.md", "Main repository page")
    addFileToProject("Page.md/placeholder.txt", "Markdown directory")
    addFileToProject("wiki/.git", "gitdir: ../.git/modules/wiki")
    addFileToProject("wiki/Main.md", "Wiki repository page")
    addFileToProject("wiki/test data.md", "Encoded page name")
    addFileToProject("wiki/dir/placeholder.txt", "Directory conflict")
    addFileToProject("wiki/dir.md", "Wiki repository page")
    addFileToProject("wiki/Page.md/placeholder.txt", "Markdown directory")
  }

  @Test
  fun testRenameWithSpaceAndMissingExtension(): Unit = links.testRenameFile(Path.of("test data.md"), "test data2.md")
}