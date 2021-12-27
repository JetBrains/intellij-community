// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi

import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceContent
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownAstFactory: ASTFactory() {
  override fun createComposite(type: IElementType): CompositeElement? {
    return when (type) {
      MarkdownElementTypes.CODE_FENCE -> MarkdownCodeFence(type)
      else -> super.createComposite(type)
    }
  }

  override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
    return when {
      type == MarkdownTokenTypes.CODE_FENCE_CONTENT -> MarkdownCodeFenceContent(type, text)
      type == MarkdownTokenTypes.TABLE_SEPARATOR && text.length > 1 -> MarkdownTableSeparatorRow(text)
      else -> super.createLeaf(type, text)
    }
  }
}
