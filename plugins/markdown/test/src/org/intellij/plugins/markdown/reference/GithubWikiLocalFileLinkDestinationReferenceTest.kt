// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.openapi.paths.WebReference
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil.unwrapMultiReference
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.references.paths.github.GithubWikiLocalFileReference
import java.nio.file.Path

class GithubWikiLocalFileLinkDestinationReferenceTest : BaseLinkDestinationReferenceTestCase() {
  override fun createTempDirTestFixture(): TempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()

  override fun setUp() {
    super.setUp()
    PsiTestUtil.addContentRoot(module, myFixture.tempDirFixture.getFile("")!!)
    myFixture.addFileToProject(".git/config", """
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

  fun testFullUrlResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  fun testFullUrlWithExtensionResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Main.md"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  fun testEnterpriseUrlResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://jetbrains.github.com/username/enterprise-repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  fun testScpLikeRemotesResolveInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/scp-repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  fun testSshRemotesResolveInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/ssh-repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/Main.md")
  }

  fun testEncodedPageNameResolvesInSourceRepository() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/test%20data"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "test data.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/test data.md")
  }

  fun testDomainLessUrlResolvesInSourceRepository() {
    createRepositoryStructure()
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    val link = "/username/repository/wiki/Main"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTargetWithoutHighlighting(mainReferences, "Main.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTargetWithoutHighlighting(wikiReferences, "wiki/Main.md")
  }

  fun testMarkdownFileWinsDirectoryConflict() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/dir"
    val mainReferences = configureLink("Links.md", link)
    assertLocalTarget(mainReferences, "dir.md")
    val wikiReferences = configureLink("wiki/Links.md", link)
    assertLocalTarget(wikiReferences, "wiki/dir.md")
  }

  fun testMarkdownDirectoryDoesNotResolveAsFile() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Page"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  fun testMissingWikiPageUsesWebReference() {
    createRepositoryStructure()
    val link = "https://github.com/username/repository/wiki/Missing"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  fun testAnotherRepositoryUsesWebReference() {
    createRepositoryStructure()
    val link = "https://github.com/AnotherOwner/AnotherProject/wiki/Main"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  fun testSameRepositoryOnAnotherHostUsesWebReference() {
    createRepositoryStructure()
    val link = "https://another.github.com/username/repository/wiki/Main"
    for (source in listOf("Links.md", "wiki/Links.md")) {
      val references = configureLink(source, link)
      assertWebReference(references)
    }
  }

  fun testMissingLocalFileRemainsUnresolved() {
    createRepositoryStructure()
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    configureMissingLocalFileLink("Links.md")
    assertUnresolvedLocalFile()
    configureMissingLocalFileLink("wiki/Links.md")
    assertUnresolvedLocalFile()
  }

  private fun assertLocalTarget(references: List<PsiReference>, expectedTarget: String) {
    val targets = references.filterIsInstance<GithubWikiLocalFileReference>().mapNotNull { it.resolve() }
    val referenceDetails = references.joinToString { "${it.javaClass.simpleName}: ${it.resolve()}" }
    assertEquals("The link must have one local target. References: $referenceDetails", 1, targets.size)
    val targetFile = assertInstanceOf(targets.single(), PsiFileSystemItem::class.java).virtualFile
    assertEquals(myFixture.findFileInTempDir(expectedTarget), targetFile)
  }

  private fun assertLocalTargetWithoutHighlighting(references: List<PsiReference>, expectedTarget: String) {
    assertLocalTarget(references, expectedTarget)
    val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
    assertFalse("The resolved link must not have an unresolved path warning: $descriptions",
                descriptions.any { it.startsWith("Cannot resolve ") })
  }

  private fun assertWebReference(references: List<PsiReference>) {
    assertTrue("The link must keep a web reference", references.any { it is WebReference })
    assertFalse("The link must not have a local target", references.filterIsInstance<GithubWikiLocalFileReference>().any { it.resolve() != null })
  }

  private fun configureLink(source: String, link: String) = with(myFixture) {
    val sourceFile = addFileToProject(source, "[Link]($link)")
    configureFromExistingVirtualFile(sourceFile.virtualFile)
    editor.caretModel.moveToOffset(file.text.indexOf(link) + link.lastIndex)
    unwrapMultiReference(file.findReferenceAt(editor.caretModel.offset)!!)
  }

  private fun configureMissingLocalFileLink(source: String) {
    val sourceFile = myFixture.addFileToProject(source, "[Missing](Missing.md)")
    myFixture.configureFromExistingVirtualFile(sourceFile.virtualFile)
  }

  private fun assertUnresolvedLocalFile() {
    val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
    assertContainsElements(descriptions, "Cannot resolve file 'Missing.md'")
  }

  private fun createRepositoryStructure() = with(myFixture) {
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

  fun testRenameWithSpaceAndMissingExtension() = testRenameFile(Path.of("test data.md"), "test data2.md")
}