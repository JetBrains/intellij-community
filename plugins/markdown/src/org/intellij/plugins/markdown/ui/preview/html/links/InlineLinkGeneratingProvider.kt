// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html.links

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.parser.LinkMap
import java.net.URI

internal class InlineLinkGeneratingProvider(baseURI: URI?): LinkGeneratingProvider(baseURI) {
  override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
    val label = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                ?: return null
    return RenderInfo(
      label,
      node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(text)?.let {
        LinkMap.normalizeDestination(it, true)
      } ?: "",
      node.findChildOfType(MarkdownElementTypes.LINK_TITLE)?.getTextInNode(text)?.let {
        LinkMap.normalizeTitle(it)
      }
    )
  }
}
