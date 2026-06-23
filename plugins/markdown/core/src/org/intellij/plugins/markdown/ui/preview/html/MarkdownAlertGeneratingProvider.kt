// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.html

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle.AlertType

internal class MarkdownAlertGeneratingProvider(
  private val fallbackProvider: GeneratingProvider,
) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val type = node.children
      .firstOrNull { it.type == GFMTokenTypes.ALERT_TITLE }
      ?.let { AlertType.fromTitleText(it.getTextInNode(text)) }
    if (type == null) {
      fallbackProvider.processNode(visitor, text, node)
      return
    }
    val typeName = type.name.lowercase()
    visitor.consumeTagOpen(node, "blockquote", "class=\"markdown-alert markdown-alert-$typeName\"")
    visitor.consumeHtml(renderTitle(type, typeName))
    node.acceptChildren(visitor)
    visitor.consumeTagClose("blockquote")
  }

  private fun renderTitle(type: AlertType, typeName: String): String {
    val label = MarkdownBundle.message("markdown.preview.alert.title.$typeName")
    val icon = MarkdownAlertIcons.resourceName(type)
    return "<p class=\"markdown-alert-title\"><img class=\"markdown-alert-icon\" src=\"$icon\" alt=\"\">$label</p>"
  }
}
