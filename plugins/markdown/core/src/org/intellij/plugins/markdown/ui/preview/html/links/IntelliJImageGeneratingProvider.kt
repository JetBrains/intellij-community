// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html.links

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import java.net.URI

internal class IntelliJImageGeneratingProvider(linkMap: LinkMap, baseURI: URI?) : LinkGeneratingProvider(baseURI) {
  companion object {
    private val REGEX = Regex("[^a-zA-Z0-9 ]")

    private fun getPlainTextFrom(node: ASTNode, text: String): CharSequence {
      return REGEX.replace(node.getTextInNode(text), "")
    }

    @JvmStatic
    val generatedAttributeName = "__idea-generated"

    @JvmStatic
    val ignorePathProcessingAttributeName = "md-do-not-process-path"
  }

  private val referenceLinkProvider = ReferenceLinksGeneratingProvider(linkMap, baseURI)
  private val inlineLinkProvider = InlineLinkGeneratingProvider(baseURI)

  override fun makeAbsoluteUrl(destination: CharSequence): CharSequence {
    val destinationEx = if (SystemInfo.isWindows) StringUtil.replace(destination.toString(), "%5C", "/") else destination.toString()
    if (destinationEx.startsWith('#')) {
      return destinationEx
    }

    return super.makeAbsoluteUrl(destinationEx)
  }

  override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
    node.findChildOfType(MarkdownElementTypes.INLINE_LINK)?.let {
      return inlineLinkProvider.getRenderInfo(text, it)
    }

    return (node.findChildOfType(MarkdownElementTypes.FULL_REFERENCE_LINK)
            ?: node.findChildOfType(MarkdownElementTypes.SHORT_REFERENCE_LINK))?.let { referenceLinkProvider.getRenderInfo(text, it) }
  }

  override fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
    val url = makeAbsoluteUrl(info.destination)
    visitor.consumeTagOpen(
      node,
      "img",
      "src=\"$url\"",
      "alt=\"${getPlainTextFrom(info.label, text)}\"",
      info.title?.let { "title=\"$it\"" },
      "$generatedAttributeName=\"true\"",
      autoClose = true
    )
  }
}
