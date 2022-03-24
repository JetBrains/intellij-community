// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html.links

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import java.net.URI

internal class ReferenceLinksGeneratingProvider(private val linkMap: LinkMap, baseURI: URI?): LinkGeneratingProvider(baseURI) {
  override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
    val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: return null
    val linkInfo = linkMap.getLinkInfo(label.getTextInNode(text)) ?: return null
    val linkTextNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
    return RenderInfo(
      linkTextNode ?: label,
      EntityConverter.replaceEntities(linkInfo.destination, true, true),
      linkInfo.title?.let { EntityConverter.replaceEntities(it, true, true) }
    )
  }
}
