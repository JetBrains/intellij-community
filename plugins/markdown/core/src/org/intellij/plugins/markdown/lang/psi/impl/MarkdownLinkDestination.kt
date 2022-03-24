// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor

@Suppress("DEPRECATION")
class MarkdownLinkDestination(node: ASTNode): MarkdownLinkDestinationImpl(node) {
  override fun accept(visitor: PsiElementVisitor) {
    @Suppress("DEPRECATION")
    when (visitor) {
      is MarkdownElementVisitor -> visitor.visitLinkDestination(this)
      else -> super.accept(visitor)
    }
  }

  override fun getReferences(): Array<PsiReference?> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this)
  }

  internal class Manipulator: AbstractElementManipulator<MarkdownLinkDestination>() {
    @Throws(IncorrectOperationException::class)
    override fun handleContentChange(element: MarkdownLinkDestination, range: TextRange, newContent: String): MarkdownLinkDestination {
      when (val child = element.firstChild) {
        is LeafPsiElement -> child.replaceWithText(range.replace(child.text, newContent))
        else -> throw IncorrectOperationException("Bad child")
      }
      return element
    }

    override fun getRangeInElement(element: MarkdownLinkDestination): TextRange {
      val text = element.text
      return when {
        text.startsWith("<") && text.endsWith(">") -> TextRange(1, text.length - 1)
        else -> TextRange.allOf(text)
      }
    }
  }
}
