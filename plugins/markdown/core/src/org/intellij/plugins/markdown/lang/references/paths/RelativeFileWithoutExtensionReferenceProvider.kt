// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.paths.PathReference
import com.intellij.openapi.paths.PathReferenceProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.references.ReferenceUtil

internal class RelativeFileWithoutExtensionReferenceProvider: PathReferenceProvider {
  override fun createReferences(psiElement: PsiElement, references: MutableList<PsiReference>, soft: Boolean): Boolean {
    if (psiElement is MarkdownPsiElement) {
      val fileReference = ReferenceUtil.findFileReference(references) ?: return false
      val reference = RelativeFileWithoutExtensionReference(psiElement, fileReference, soft)
      if (reference.resolve() == null) return false
      references.add(reference)
    }
    return false
  }

  override fun getPathReference(path: String, element: PsiElement): PathReference? {
    return null
  }
}
