// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor

class MarkdownParagraph(node: ASTNode): MarkdownParagraphImpl(node) {
  override fun accept(visitor: PsiElementVisitor) {
    @Suppress("DEPRECATION")
    when (visitor) {
      is MarkdownElementVisitor -> visitor.visitParagraph(this)
      else -> super.accept(visitor)
    }
  }

  override fun getPresentableTagName(): String {
    return "p"
  }
}
