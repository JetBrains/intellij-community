// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TransparentInlineHolderProvider
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import java.net.URI

internal abstract class LinkGeneratingProvider(private val baseURI: URI?) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val info = getRenderInfo(text, node)
               ?: return fallbackProvider.processNode(visitor, text, node)
    renderLink(visitor, text, node, info)
  }

  protected open fun makeAbsoluteUrl(destination: CharSequence): CharSequence {
    if (destination.startsWith('#')) {
      return destination
    }


    try {
      return baseURI?.resolve(destination.toString())?.toString() ?: destination
    }
    catch (e: IllegalArgumentException) {
      return destination
    }
  }

  open fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
    visitor.consumeTagOpen(node, "a", "href=\"${makeAbsoluteUrl(info.destination)}\"", info.title?.let { "title=\"$it\"" })
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

internal class IntelliJImageGeneratingProvider(linkMap: LinkMap, baseURI: URI?) : LinkGeneratingProvider(baseURI) {
  companion object {
    private val REGEX = Regex("[^a-zA-Z0-9 ]")

    private fun getPlainTextFrom(node: ASTNode, text: String): CharSequence {
      return REGEX.replace(node.getTextInNode(text), "")
    }
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
            ?: node.findChildOfType(MarkdownElementTypes.SHORT_REFERENCE_LINK))?.let {
      referenceLinkProvider.getRenderInfo(text, it)
    }
  }

  override fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
    visitor.consumeTagOpen(node, "img",
                           "src=\"${makeAbsoluteUrl(info.destination)}\"",
                           "alt=\"${getPlainTextFrom(info.label, text)}\"",
                           info.title?.let { "title=\"$it\"" },
                           autoClose = true)
  }
}

internal class ReferenceLinksGeneratingProvider(private val linkMap: LinkMap, baseURI: URI?)
  : LinkGeneratingProvider(baseURI) {
  override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
    val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                ?: return null
    val linkInfo = linkMap.getLinkInfo(label.getTextInNode(text))
                   ?: return null
    val linkTextNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }

    return RenderInfo(
      linkTextNode ?: label,
      EntityConverter.replaceEntities(linkInfo.destination, true, true),
      linkInfo.title?.let { EntityConverter.replaceEntities(it, true, true) }
    )
  }
}

internal class InlineLinkGeneratingProvider(baseURI: URI?) : LinkGeneratingProvider(baseURI) {
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
