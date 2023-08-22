// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TrimmingInlineHolderProvider

internal class ParagraphGeneratingProvider : TrimmingInlineHolderProvider() {
  override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeTagOpen(node, "p")
  }

  override fun closeTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeTagClose("p")
  }

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    openTag(visitor, text, node)
    for (child in childrenToRender(node)) {
      if (child is LeafASTNode) {
        when (child.type) {
          MarkdownTokenTypes.TEXT -> {
            var left = child.startOffset
            for (line in child.getTextInNode(text).split("\n")) {
              val right = left + line.length
              visitor.consumeHtml(
                "<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='$left..$right'>${DefaultCodeFenceGeneratingProvider.escape(line)}</span>"
              )
              left += right + 1
            }
          }
          else -> visitor.visitLeaf(child)
        }
      } else {
        child.accept(visitor)
      }
    }
    closeTag(visitor, text, node)
  }
}
