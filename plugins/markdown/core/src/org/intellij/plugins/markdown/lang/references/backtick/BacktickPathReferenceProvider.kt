// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.backtick

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import org.intellij.plugins.markdown.lang.references.ReferenceUtil.isRelativePathLike

internal object BacktickPathReferenceProvider {
  private const val CLAUDE_SKILL_DIR = $$"${CLAUDE_SKILL_DIR}"
  private const val SKILL_MD = "SKILL.md"

  fun getReferences(codeSpan: MarkdownCodeSpan, contentRange: TextRange, content: String): Array<PsiReference> {
    val pathReference = parsePathReference(contentRange.substring(codeSpan.text)) ?: return PsiReference.EMPTY_ARRAY
    val contexts = pathReference.getContexts(codeSpan)
    if (contexts.isEmpty()) return PsiReference.EMPTY_ARRAY

    val startOffset = contentRange.startOffset + pathReference.startOffsetInContent
    val references = object : FileReferenceSet(pathReference.path, codeSpan, startOffset, null, true, false) {
      override fun isSoft(): Boolean = true
      override fun computeDefaultContexts(): Collection<PsiFileSystemItem> = contexts

      override fun createFileReference(range: TextRange, index: Int, text: String): FileReference? {
        if (pathReference.type == PathType.Skill && index == 0 && text == CLAUDE_SKILL_DIR) {
          return SkillDirectoryFileReference(this, range, index, text, contexts.first())
        }
        return super.createFileReference(range, index, text)
      }
    }.allReferences
    return Array(references.size) { references[it] }
  }

  private fun parsePathReference(text: String): PathReferenceInfo? {
    if (text.isEmpty() || text.any(Char::isWhitespace) || text.contains("://")) return null
    if (text.startsWith(CLAUDE_SKILL_DIR)) {
      val suffix = text.removePrefix(CLAUDE_SKILL_DIR)
      return when {
        suffix.isEmpty() || suffix.startsWith('/') -> PathReferenceInfo(text, 0, PathType.Skill)
        else -> null
      }
    }
    if (!text.isRelativePathLike()) return null
    return PathReferenceInfo(text, 0, PathType.Project)
  }

  private fun PathReferenceInfo.getContexts(codeSpan: MarkdownCodeSpan): Collection<PsiFileSystemItem> {
    val file = codeSpan.containingFile?.originalFile ?: return emptyList()
    val virtualFile = file.virtualFile ?: return emptyList()
    val projectDirectory = BaseProjectDirectories.getInstance(file.project).getBaseDirectoryFor(virtualFile)
    val directories = when (type) {
      PathType.Skill -> setOfNotNull(findSkillDirectory(virtualFile, projectDirectory))
      PathType.Project -> {
        if (path.startsWith("./") || path.startsWith("../")) {
          setOfNotNull(virtualFile.parent)
        }
        else {
          setOfNotNull(projectDirectory, virtualFile.parent)
        }
      }
    }
    return directories
      .filter { it.isValid && it.isDirectory }
      .mapNotNull { file.manager.findDirectory(it) }
  }

  private fun findSkillDirectory(file: VirtualFile, projectDirectory: VirtualFile?): VirtualFile? {
    var directory = if (file.isDirectory) file else file.parent
    while (directory != null) {
      if (projectDirectory != null && !VfsUtilCore.isAncestor(projectDirectory, directory, false)) {
        return null
      }
      if (directory.findChild(SKILL_MD)?.isDirectory == false) {
        return directory
      }
      if (directory == projectDirectory) return null
      directory = directory.parent
    }
    return null
  }

  private class SkillDirectoryFileReference(
    fileReferenceSet: FileReferenceSet,
    range: TextRange,
    index: Int,
    text: String,
    private val target: PsiFileSystemItem,
  ) : FileReference(fileReferenceSet, range, index, text) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = arrayOf(PsiElementResolveResult(target))
    override fun resolve(): PsiFileSystemItem = target
    override fun isReferenceTo(element: PsiElement): Boolean = element.manager.areElementsEquivalent(target, element)
  }

  private data class PathReferenceInfo(val path: String, val startOffsetInContent: Int, val type: PathType)
  private enum class PathType { Project, Skill }
}