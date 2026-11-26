// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.paths.PathReference
import com.intellij.openapi.paths.PathReferenceProviderBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.references.ReferenceUtil

/**
 * Creates the file references of a Markdown link destination. They read and write the backslash escapes of CommonMark.
 * The provider runs first and replaces the file references of [com.intellij.openapi.paths.StaticPathReferenceProvider].
 */
internal class MarkdownFileReferenceProvider : PathReferenceProviderBase() {
  override fun createReferences(
    psiElement: PsiElement, offset: Int, text: String, references: MutableList<in PsiReference>, soft: Boolean,
  ): Boolean {
    if (psiElement !is MarkdownLinkDestination) return false
    references.removeIf { it is FileReference }
    val referenceSet = object : FileReferenceSet(text, psiElement, offset, null, true, false, null) {
      override fun isUrlEncoded(): Boolean = true
      override fun isSoft(): Boolean = soft
      override fun createFileReference(range: TextRange, index: Int, text: String): FileReference {
        return MarkdownFileReference(this, range, index, text)
      }
    }
    references.addAll(referenceSet.allReferences)
    return false
  }

  override fun getPathReference(path: String, element: PsiElement): PathReference? = null
}

private class MarkdownFileReference(referenceSet: FileReferenceSet, range: TextRange, index: Int, text: String) :
  FileReference(referenceSet, range, index, text) {
  override fun decode(text: String): String = super.decode(ReferenceUtil.removeBackslashEscapes(text))

  override fun encode(name: String, psiElement: PsiElement): String? = super.encode(name, psiElement)?.let(ReferenceUtil::escapeParentheses)

  override fun rename(newName: String): PsiElement = super.rename(ReferenceUtil.escapeParentheses(newName))

  override fun handleElementRename(newElementName: String): PsiElement =
    super.handleElementRename(ReferenceUtil.escapeParentheses(newElementName))
}