// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor

open class MarkdownParagraph(node: ASTNode): MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost {
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
