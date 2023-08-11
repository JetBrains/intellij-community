// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi

import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownAstFactory: ASTFactory() {
  override fun createComposite(type: IElementType): CompositeElement? {
    return when (type) {
      MarkdownElementTypes.CODE_FENCE -> MarkdownCodeFence(type)
      MarkdownElementTypes.FRONT_MATTER_HEADER -> MarkdownFrontMatterHeader(type)
      else -> super.createComposite(type)
    }
  }

  override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
    return when {
      type == MarkdownTokenTypes.LIST_NUMBER -> MarkdownListNumber(type, text)
      type == MarkdownTokenTypes.CODE_FENCE_CONTENT -> MarkdownCodeFenceContent(type, text)
      type == MarkdownElementTypes.FRONT_MATTER_HEADER_CONTENT -> MarkdownFrontMatterHeaderContent(type, text)
      type == MarkdownTokenTypes.TABLE_SEPARATOR && text.length > 1 -> MarkdownTableSeparatorRow(text)
      type == MarkdownTokenTypes.TABLE_SEPARATOR && text.toString() == "|" -> MarkdownTableSeparator(text)
      type == MarkdownElementTypes.COMMENT_VALUE -> MarkdownCommentValue(text)
      type in MarkdownTokenTypeSets.AUTO_LINKS -> MarkdownAutoLink(type, text)
      else -> super.createLeaf(type, text)
    }
  }
}
