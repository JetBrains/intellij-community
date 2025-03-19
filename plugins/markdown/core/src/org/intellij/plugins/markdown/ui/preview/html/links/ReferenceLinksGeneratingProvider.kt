// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html.links

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap

internal class ReferenceLinksGeneratingProvider(private val linkMap: LinkMap): LinkGeneratingProvider() {
  override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
    val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: return null
    val linkInfo = linkMap.getLinkInfo(label.getTextInNode(text)) ?: return null
    val linkTextNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
    val destination = linkInfo.node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(text) ?: ""
    return RenderInfo(
      linkTextNode ?: label,
      destination,
      linkInfo.title?.let { EntityConverter.replaceEntities(it, true, true) }
    )
  }
}
