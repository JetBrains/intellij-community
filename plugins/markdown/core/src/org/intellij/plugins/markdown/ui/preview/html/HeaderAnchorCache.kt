// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.html

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.plugins.markdown.lang.MarkdownElementType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader.Companion.createUniqueAnchorText
import org.jetbrains.annotations.ApiStatus
import java.util.IdentityHashMap

@ApiStatus.Internal
class HeaderAnchorCache {
  private var cachedFile: ASTNode? = null
  private var cachedAnchors: Map<ASTNode, CachedAnchor>? = null

  fun buildUniqueAnchorText(node: ASTNode, fileText: String): String? {
    val file = node.getParentOfType(MarkdownElementTypes.MARKDOWN_FILE) ?: return null
    if (cachedFile !== file) {
      cachedFile = file
      cachedAnchors = buildAnchors(file, fileText)
    }
    val anchors = cachedAnchors ?: return null
    val cachedAnchor = anchors[node] ?: return null
    val anchorText = cachedAnchor.rawAnchorText ?: return null
    return createUniqueAnchorText(anchorText, cachedAnchor.uniqueNumber)
  }

  private fun buildAnchors(file: ASTNode, fileText: String): Map<ASTNode, CachedAnchor> {
    val anchors = IdentityHashMap<ASTNode, CachedAnchor>()
    val headerOccurrences = mutableMapOf<String, Int>()
    val headers = file.traverse().filter { MarkdownElementType.isHeaderElementType(it.type) }
    for (header in headers) {
      val rawAnchorText = HeaderGeneratingProvider.buildAnchorText(header, fileText)
      val currentOccurrence = if (rawAnchorText != null) {
        headerOccurrences.getOrDefault(rawAnchorText, 0)
      } else {
        0
      }
      anchors[header] = CachedAnchor(rawAnchorText, currentOccurrence)
      if (rawAnchorText != null) {
        headerOccurrences[rawAnchorText] = currentOccurrence + 1
      }
    }
    return anchors
  }

  private data class CachedAnchor(
    val rawAnchorText: String?,
    val uniqueNumber: Int,
  )
}
