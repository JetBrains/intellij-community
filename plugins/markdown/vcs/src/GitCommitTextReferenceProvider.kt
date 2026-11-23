// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.vcs

import com.intellij.model.Pointer
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.elementType
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.references.MarkdownTextReferenceProvider
import org.jetbrains.annotations.Nls

internal class GitCommitTextReferenceProvider : MarkdownTextReferenceProvider {
  override fun getReferences(element: PsiElement): Array<PsiReference>? {
    when (element) {
      is MarkdownParagraph, is MarkdownHeader, is MarkdownTableCell -> {
        return findGitCommitReferences(element).takeIf { it.isNotEmpty() }
      }
      else -> return null
    }
  }
}

/** Gives the hint that the editor shows for a SHA reference, in place of the class name of [GitCommitNavigatableElement]. */
internal class GitCommitDocumentationTargetProvider : PsiDocumentationTargetProvider {
  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    val target = element as? GitCommitNavigatableElement ?: return null
    return GitCommitDocumentationTarget(target.hash, target.getQuickNavigateInfo())
  }
}

private class GitCommitDocumentationTarget(private val hash: @NlsSafe String, private val hint: @Nls String) : DocumentationTarget {
  override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation = TargetPresentation.builder(hash).presentation()

  override val navigatable: Navigatable? get() = null

  override fun computeDocumentationHint(): @Nls String = hint
}

/**
 * Returns candidate commit references in plain text of a file under a VCS root that the VCS Log supports.
 * The VCS Log searches for each hash when the user navigates to it.
 */
internal fun findGitCommitReferences(host: PsiElement): Array<PsiReference> {
  if (host.project.isDefault || findVcsRoot(host) == null) return PsiReference.EMPTY_ARRAY
  return getCachedGitCommitReferences(host)
}

private fun findVcsRoot(element: PsiElement): VirtualFile? {
  val project = element.project
  val file = element.containingFile?.originalFile?.virtualFile ?: return null
  val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file) ?: return null
  if (VcsLogManager.findLogProviders(listOf(vcsRoot), project).isEmpty()) return null
  return vcsRoot.path
}

private fun getCachedGitCommitReferences(host: PsiElement): Array<PsiReference> = CachedValuesManager.getCachedValue(host) {
  CachedValueProvider.Result.create(findTextReferences(host), host)
}

private fun findTextReferences(host: PsiElement): Array<PsiReference> {
  val hostText = host.text
  val cancellableText = object : StringUtil.BombedCharSequence(hostText) {
    override fun checkCanceled() = ProgressManager.checkCanceled()
  }
  return SHA_REFERENCE.findAll(cancellableText).mapNotNull { match ->
    val hashRange = match.groups[1]!!.range
    val hash = hostText.substring(hashRange)
    if (!hash.isGitCommitHash() || hash.length !in SUPPORTED_HASH_LENGTHS) return@mapNotNull null
    if (!isWordBoundary(host, hostText, match.range.first - 1) || !isWordBoundary(host, hostText, match.range.last + 1)) return@mapNotNull null
    if (!isPlainText(host, match.range.first) || !isPlainText(host, match.range.last)) return@mapNotNull null
    GitCommitReference(host, TextRange(hashRange.first, hashRange.last + 1), hash)
  }.toList().toTypedArray()
}

/** Returns true when the character at [offset] of [host] is plain text, outside a code span, a link or other excluded element. */
private fun isPlainText(host: PsiElement, offset: Int): Boolean {
  val leaf = host.findElementAt(offset) ?: return false
  return leaf.elementType == MarkdownTokenTypes.TEXT && !isInsideExcludedElement(leaf, host)
}

private fun isInsideExcludedElement(element: PsiElement, host: PsiElement): Boolean {
  var current = element
  while (current != host) {
    if (current.elementType in EXCLUDED_ELEMENT_TYPES || MarkdownTokenTypeSets.AUTO_LINKS.contains(current.elementType)) {
      return true
    }
    current = current.parent ?: return false
  }
  return false
}

private fun String.isGitCommitHash(): Boolean = any(Char::isDigit) && any { it in 'a'..'f' || it in 'A'..'F' }

/**
 * Returns true when the character at [offset] of [host] is not `_` or `=`, or is a delimiter of an emphasis, as in `_text_` or `==text==`.
 * [SHA_REFERENCE] checks all other boundary characters.
 */
private fun isWordBoundary(host: PsiElement, hostText: String, offset: Int): Boolean {
  val char = hostText.getOrNull(offset)
  if (char != '_' && char != '=') return true
  val leaf = host.findElementAt(offset) ?: return false
  return leaf.elementType != MarkdownTokenTypes.TEXT && leaf.parent?.elementType in MarkdownTokenTypeSets.EMPHASIS_LIKE_ELEMENTS
}

private class GitCommitReference(
  element: PsiElement,
  range: TextRange,
  private val hash: String,
) : PsiReferenceBase<PsiElement>(element, range, false) {
  override fun resolve(): PsiElement = GitCommitNavigatableElement(element, hash)

  override fun getVariants(): Array<Any> = emptyArray()
}

private class GitCommitNavigatableElement(
  private val source: PsiElement,
  val hash: String,
) : FakePsiElement(), Navigatable {
  override fun getParent(): PsiElement = source

  override fun getText(): String = hash

  override fun navigate(requestFocus: Boolean) {
    project.service<GitCommitNavigator>().showCommit(findVcsRoot(source), hash)
  }

  override fun canNavigate(): Boolean = VcsProjectLog.isAvailable(project)

  override fun canNavigateToSource(): Boolean = false

  fun getQuickNavigateInfo(): @Nls String {
    val file = source.containingFile?.originalFile?.virtualFile
    val vcsName = file?.let { ProjectLevelVcsManager.getInstance(project).getVcsFor(it)?.displayName }
                  ?: MarkdownBundle.message("markdown.vcs.commit.reference.default.vcs.name")
    return MarkdownBundle.message("markdown.vcs.commit.reference.hint", hash, vcsName)
  }
}

private val EXCLUDED_ELEMENT_TYPES = setOf(
  MarkdownElementTypes.CODE_SPAN,
  MarkdownElementTypes.CODE_FENCE,
  MarkdownElementTypes.CODE_BLOCK,
  MarkdownElementTypes.LINK_TEXT,
  MarkdownElementTypes.LINK_DESTINATION,
  MarkdownElementTypes.LINK_TITLE,
  MarkdownElementTypes.LINK_DEFINITION,
  MarkdownElementTypes.INLINE_LINK,
  MarkdownElementTypes.FULL_REFERENCE_LINK,
  MarkdownElementTypes.SHORT_REFERENCE_LINK,
  MarkdownElementTypes.IMAGE,
  MarkdownElementTypes.TEST_LINK,
  MarkdownElementTypes.AUTOLINK,
  MarkdownElementTypes.HTML_BLOCK,
  MarkdownElementTypes.LINK_COMMENT,
)

private val SUPPORTED_HASH_LENGTHS = (7..16).toSet() + setOf(40, 64)

/**
 * Finds a hash with an optional GitHub `owner@` or `owner/repo@` prefix.
 * The hash must not touch a word, a path, a URL or an email.
 * A period after the hash is allowed when it ends a sentence.
 * An `_` or `=` next to the hash is allowed only for an emphasis, so [isWordBoundary] checks it.
 */
private val SHA_REFERENCE = Regex(
  "(?<![A-Za-z0-9./#?&@%-])" +
  "(?:[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?@)?" +
  "([0-9a-fA-F]{7,64})" +
  "(?![A-Za-z0-9/@-])(?!\\.[A-Za-z0-9_/-])",
)
