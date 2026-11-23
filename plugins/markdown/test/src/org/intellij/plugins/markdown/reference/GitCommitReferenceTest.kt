// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.pom.Navigatable
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerExtension
import com.intellij.vcs.log.VcsLogProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

@TestApplication
class GitCommitReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val FULL_HASH = "3f9c2e1d8b7a6c5d4e3f2a1b0c9d8e7f6a5b4c3d"

    private val FULL_SHA256_HASH = "6b8e0d2f".padEnd(64, '1')
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private var isVcsRootRegistered = false

  @Test
  fun `test full SHA candidate`() {
    assertNavigatesToCommit(FULL_HASH, FULL_HASH)
  }

  @Test
  fun `test short SHA candidate`() {
    assertNavigatesToCommit("3f9c2e1", "3f9c2e1")
  }

  @Test
  fun `test SHA before a sentence period`() {
    assertNavigatesToCommit("fixed in 3f9c2e1.", "3f9c2e1")
    assertNavigatesToCommit("(see 3f9c2e1.)", "3f9c2e1")
    assertNavigatesToCommit("fixed in _3f9c2e1_", "3f9c2e1")
    assertNavigatesToCommit("fixed in __3f9c2e1__", "3f9c2e1")
    assertNavigatesToCommit("fixed in ==3f9c2e1==", "3f9c2e1")
    assertNavigatesToCommit("fixed in ~~3f9c2e1~~", "3f9c2e1")
  }

  @Test
  fun `test SHA in heading`() {
    assertNavigatesToCommit("# Commit 3f9c2e1", "3f9c2e1")
  }

  @Test
  fun `test SHA in table cell`() {
    assertNavigatesToCommit("| Commit | Note |\n|---|---|\n| 3f9c2e1 | Fix crash |\n", "3f9c2e1")
  }

  @Test
  fun `test SHA with a GitHub owner or repository covers only the hash`() {
    assertNavigatesToCommit("owner@3f9c2e1", "3f9c2e1")
    assertNavigatesToCommit("owner/repo@3f9c2e1", "3f9c2e1")
  }

  @Test
  fun `test full SHA-256 candidate`() {
    assertNavigatesToCommit(FULL_SHA256_HASH, FULL_SHA256_HASH)
  }

  @Test
  fun `test supported abbreviated SHA lengths`() {
    assertNavigatesToCommit("fixed in 3f9c2e1d8b7a", "3f9c2e1d8b7a")
    assertNavigatesToCommit("fixed in 3f9c2e1d00", "3f9c2e1d00")
    assertNavigatesToCommit("fixed in 3f9c2e1d8b7a6c5d", "3f9c2e1d8b7a6c5d")
    assertNoCommitReference("fixed in 3f9c2e1d8b7a6c5d4", "3f9c2e1d8b7a6c5d4")
  }

  @Test
  fun `test unknown SHA is still a candidate reference`() {
    assertNavigatesToCommit("7654321abc", "7654321abc")
  }

  @Test
  fun `test SHA reference has a short hint`() {
    withReferences("fixed in 3f9c2e1", "3f9c2e1") { references ->
      val target = references.first { it.isCommitReference("3f9c2e1") }.resolve()!!
      val hint = psiDocumentationTargets(target, null).single().computeDocumentationHint()
      assertEquals("Show commit 3f9c2e1 in ${MockAbstractVcs(myFixture.project).displayName} Log", hint)
    }
  }

  @Test
  fun `test SHA under a VCS without a log is not a reference`() {
    assertNoCommitReference("fixed in 3f9c2e1", "3f9c2e1", underVcs = false) {
      registerVcsRoot(myFixture.tempDirPath, hasLog = false)
    }
  }

  @Test
  fun `test SHA outside a VCS root is not a reference`() {
    assertNoCommitReference("fixed in 3f9c2e1", "3f9c2e1", underVcs = false)
  }

  @Test
  fun `test GitHub commit URL autolink opens in browser`() {
    assertOpensGitHubCommitUrlInBrowser("https://github.com/owner/repo/commit/$FULL_HASH")
  }

  @Test
  fun `test GitHub CommonMark autolink opens in browser`() {
    assertOpensGitHubCommitUrlInBrowser("<https://github.com/owner/repo/commit/$FULL_HASH>")
  }

  @Test
  fun `test GitHub commit URL in Markdown link opens in browser`() {
    assertOpensGitHubCommitUrlInBrowser("[commit](https://github.com/owner/repo/commit/$FULL_HASH)")
  }

  @Test
  fun `test code span is not a commit reference`() {
    assertNoCommitReference("`3f9c2e1`", "3f9c2e1")
  }

  @Test
  fun `test numbers and hexadecimal words are not commit references`() {
    for (text in listOf("1000000", "deadbeef", "defaced")) {
      assertNoCommitReference(text, text)
    }
  }

  @Test
  fun `test SHA-like fragments in URLs are not commit references`() {
    assertNoCommitReference("https://example.com/page#1234567abc", "1234567abc")
    assertNoCommitReference("[x](https://host/?c=1234567abc)", "1234567abc")
  }

  @Test
  fun `test SHA-like filename UUID and path are not commit references`() {
    assertNoCommitReference("a1b2c3d4.png", "a1b2c3d4")
    assertNoCommitReference("550e8400-e29b-41d4-...", "550e8400")
    assertNoCommitReference("3f9c2e1/path", "3f9c2e1")
    assertNoCommitReference("build_1234567abc", "1234567abc")
    assertNoCommitReference("commit=1234567abc", "1234567abc")
    assertNoCommitReference("mail a1b2c3d4@example.com", "a1b2c3d4")
    assertNoCommitReference("3f9c2e1b".padEnd(50, '1'), "3f9c2e1b")
  }

  private fun assertNavigatesToCommit(content: String, expectedHash: String) {
    withReferences(content, expectedHash) { references ->
      val commitReference = references.firstOrNull { it.isCommitReference(expectedHash) }
      assertNotNull(commitReference, "No commit reference at the SHA in $content")
      assertEquals(expectedHash, commitReference!!.rangeInElement.substring(commitReference.element.text))
    }
  }

  private fun assertNoCommitReference(content: String, needle: String, underVcs: Boolean = true, setUp: () -> Unit = {}) {
    withReferences(content, needle, underVcs, setUp) { references ->
      assertFalse(references.any { it.isCommitReference(needle) }, "Unexpected commit reference in $content")
    }
  }

  private fun assertOpensGitHubCommitUrlInBrowser(content: String) {
    withReferences(content, FULL_HASH) { references ->
      assertTrue(references.any { it is WebReference }, "Expected the GitHub URL to keep its web reference")
      assertFalse(references.any { it.isCommitReference(FULL_HASH) }, "GitHub URLs should not offer Git Log navigation")
    }
  }

  private fun withReferences(
    content: String,
    needle: String,
    underVcs: Boolean = true,
    setUp: () -> Unit = {},
    assertion: (List<PsiReference>) -> Unit,
  ): Unit = timeoutRunBlocking {
    myFixture.configureByText("test.md", content)
    if (underVcs) registerVcsRoot(myFixture.tempDirPath)
    setUp()
    readActionBlocking {
      assertion(findReferences(myFixture.file, myFixture.file.text.indexOf(needle)))
    }
  }

  /** Maps [path] to a mock VCS. With [hasLog], the VCS Log supports the mock VCS. */
  private fun registerVcsRoot(path: String, hasLog: Boolean = true) {
    if (isVcsRootRegistered) return
    isVcsRootRegistered = true
    val project = myFixture.project
    val vcsManager = ProjectLevelVcsManagerImpl.getInstanceImpl(project)
    val vcs = MockAbstractVcs(project)
    vcsManager.registerVcs(vcs)
    if (hasLog) {
      project.registerExtension(VcsLogProvider.LOG_PROVIDER_EP, createLogProvider(vcs), myFixture.testRootDisposable)
    }
    Disposer.register(myFixture.testRootDisposable) {
      vcsManager.setDirectoryMappings(emptyList())
      vcsManager.unregisterVcs(vcs)
    }
    vcsManager.waitForInitialized()
    vcsManager.setDirectoryMapping(path, vcs.name)
  }

  /** Creates a log provider that only gives the key of [vcs]. The test does not create the VCS Log, so no other method runs. */
  private fun createLogProvider(vcs: MockAbstractVcs): VcsLogProvider {
    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(VcsLogProvider::class.java)) { proxy, method, args ->
      when (method.name) {
        "getSupportedVcs" -> vcs.keyInstanceMethod
        "equals" -> proxy === args?.get(0)
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "TestLogProvider(${vcs.name})"
        else -> throw UnsupportedOperationException(method.name)
      }
    } as VcsLogProvider
  }

  private fun findReferences(file: PsiFile, offset: Int): List<PsiReference> {
    val reference = file.findReferenceAt(offset) ?: return emptyList()
    return when (reference) {
      is PsiMultiReference -> reference.references.toList()
      else -> listOf(reference)
    }
  }

  private fun PsiReference.isCommitReference(hash: String): Boolean {
    val target = resolve()
    return this !is WebReference && target is Navigatable && target.text == hash
  }
}
