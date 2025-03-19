// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html.links

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TransparentInlineHolderProvider
import org.intellij.markdown.parser.LinkMap

internal abstract class LinkGeneratingProvider: GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val info = getRenderInfo(text, node) ?: return fallbackProvider.processNode(visitor, text, node)
    renderLink(visitor, text, node, info)
  }

  open fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
    val destination = info.destination.let { LinkMap.normalizeDestination(it, true) }
    visitor.consumeTagOpen(node, "a", "href=\"$destination\"", info.title?.let { "title=\"$it\"" })
    labelProvider.processNode(visitor, text, info.label)
    visitor.consumeTagClose("a")
  }

  abstract fun getRenderInfo(text: String, node: ASTNode): RenderInfo?

  data class RenderInfo(val label: ASTNode, val destination: CharSequence, val title: CharSequence?)

  companion object {
    val fallbackProvider = TransparentInlineHolderProvider()

    val labelProvider = TransparentInlineHolderProvider(1, -1)
  }
}
