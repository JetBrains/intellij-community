// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.references.MarkdownTextReferenceProvider

class MarkdownParagraph(node: ASTNode): MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost {
  override fun getReferences(): Array<PsiReference> {
    return MarkdownTextReferenceProvider.getReferencesFor(this) ?: PsiReference.EMPTY_ARRAY
  }

  override fun accept(visitor: PsiElementVisitor) {
    when (visitor) {
      is MarkdownElementVisitor -> visitor.visitParagraph(this)
      else -> super.accept(visitor)
    }
  }

  override fun getPresentableTagName(): String {
    return "p"
  }
}
