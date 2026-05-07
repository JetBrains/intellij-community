// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.paths.PathReference
import com.intellij.openapi.paths.PathReferenceProviderBase
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.FakePsiElement
import org.intellij.plugins.markdown.lang.isMarkdownType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.references.ReferenceUtil
import org.intellij.plugins.markdown.util.MarkdownLinkFragmentUtil

internal class LineNumberPathReferenceProvider: PathReferenceProviderBase() {
  override fun createReferences(
    element: PsiElement,
    offset: Int,
    text: String?,
    references: MutableList<in PsiReference>,
    soft: Boolean
  ): Boolean {
    if (element !is MarkdownLinkDestination) {
      return false
    }
    val range = ElementManipulators.getValueTextRange(element)
    val elementText = element.text
    val fragmentRange = MarkdownLinkFragmentUtil.getFragmentRange(elementText, range) ?: return false
    val lineRange = MarkdownLinkFragmentUtil.parseGitHubLineRange(fragmentRange.substring(elementText)) ?: return false
    val targetFile = findTargetFile(references) ?: return false
    if (targetFile.fileType.isMarkdownType()) {
      return false
    }
    references.add(object : PsiReferenceBase<MarkdownLinkDestination>(element, TextRange(fragmentRange.startOffset, fragmentRange.startOffset + fragmentRange.length), false) {
      override fun resolve(): PsiElement {
        return MarkdownLineNumberTarget(targetFile, lineRange.first)
      }
    })
    return false
  }

  override fun getPathReference(path: String, element: PsiElement): PathReference? {
    return null
  }

  private fun findTargetFile(references: MutableList<in PsiReference>): PsiFile? {
    val actualReference = ReferenceUtil.findFileReference(references)
    val resolvedFromFileReference = actualReference?.resolve() as? PsiFile
    if (resolvedFromFileReference != null) {
      return resolvedFromFileReference
    }
    val fileWithoutExtensionReference = references.asSequence().filterIsInstance<FileWithoutExtensionReference>().firstOrNull()
    return fileWithoutExtensionReference?.resolve() as? PsiFile
  }

  private class MarkdownLineNumberTarget(
    private val file: PsiFile,
    private val lineNumber: Int
  ): FakePsiElement(), NavigatablePsiElement {
    override fun getName(): String {
      return "${file.name}:L${lineNumber + 1}"
    }

    override fun getParent(): PsiElement {
      return file
    }

    override fun getContainingFile(): PsiFile {
      return file
    }

    override fun navigate(requestFocus: Boolean) {
      val virtualFile = file.virtualFile ?: return
      OpenFileDescriptor(file.project, virtualFile, lineNumber, 0).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean {
      return file.virtualFile != null
    }

    override fun canNavigateToSource(): Boolean {
      return canNavigate()
    }
  }
}
