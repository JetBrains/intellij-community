// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.jcef.preview.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.html.PreviewEncodingUtil
import org.intellij.plugins.markdown.ui.preview.html.links.IntelliJImageGeneratingProvider
import org.jetbrains.annotations.ApiStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import org.jsoup.parser.TagSet

@ApiStatus.Internal
class IncrementalDOMBuilder(
  html: String,
  private val sourceFile: VirtualFile?,
  private val imageResourceProvider: ResourceProvider? = null,
) {

  private val document = Jsoup.parse(html, createSelfClosingSpanAwareParser())
  private val builder = StringBuilder()

  fun generateRenderClosure(): String {
    // language=JavaScript
    return """
      () => {
        const o = (tag, ...attrs) => IncrementalDOM.elementOpen(tag, null, null, ...attrs.map(decodeURIComponent));
        const t = content => IncrementalDOM.text(decodeURIComponent(content));
        const c = IncrementalDOM.elementClose;
        ${generateDomBuildCalls()}
      }
    """
  }

  fun generateDomBuildCalls(): String {
    traverse(document.body())
    return builder.toString()
  }

  private fun ensureCorrectTag(name: String): String {
    return when (name) {
      "body" -> "div"
      else -> name
    }
  }

  private fun encodeArgument(argument: String): String {
    return PreviewEncodingUtil.encodeUrl(argument)
  }

  private fun openTag(node: Node) {
    with(builder) {
      append("o('")
      append(ensureCorrectTag(node.nodeName()))
      append("'")
      for (attribute in node.attributes()) {
        append(",'")
        append(attribute.key)
        append('\'')
        val value = attribute.value
        @Suppress("SENSELESS_COMPARISON")
        if (value != null) {
          append(",'")
          append(encodeArgument(value))
          append("'")
        }
      }
      append(");")
    }
  }

  private fun closeTag(node: Node) {
    with(builder) {
      append("c('")
      append(ensureCorrectTag(node.nodeName()))
      append("');")
    }
  }

  private fun textElement(getter: () -> String) {
    with(builder) {
      // It seems like CefBrowser::executeJavaScript() is not supporting a lot of unicode
      // symbols (like emojis) in the code string (probably a limitation of CefString).
      // To preserve these symbols, we are encoding our strings before sending them to JCEF,
      // and decoding them before executing the code.
      // For our use case it's enough to encode just the actual text content that
      // will be displayed (only IncrementalDOM.text() calls).
      append("t(`")
      append(encodeArgument(getter.invoke()))
      append("`);")
    }
  }

  /**
   * Points the `src` of an image node at [imageResourceProvider], which resolves it later.
   *
   * This needs no path of the document, and a Remote Development frontend has none.
   */
  private fun preprocessNode(node: Node): Node {
    stripReferrerPolicy(node)
    val provider = imageResourceProvider
    if (sourceFile == null || provider == null || !shouldPreprocessImageNode(node)) {
      return node
    }
    val source = node.attr("src")
    if (source.isEmpty() || MarkdownImagePathResolver.isBrowserOwned(source)) {
      return node
    }
    try {
      node.attr("data-original-src", source)
      node.attr("src", PreviewStaticServer.getStaticUrl(provider, MarkdownImageResourceProvider.resourceName(source)))
    }
    catch (exception: Throwable) {
      thisLogger().warn("Failed to rewrite the source of an image node: $source", exception)
    }
    return node
  }

  private fun shouldPreprocessImageNode(node: Node): Boolean {
    return node.nodeName() == "img" && !node.hasAttr(IntelliJImageGeneratingProvider.ignorePathProcessingAttributeName)
  }

  /**
   * An element-level `referrerpolicy` overrides the page's `no-referrer`, which a document can use to send
   * the page URL - and with it this preview's resource paths - to any host (IJPL-247809).
   */
  private fun stripReferrerPolicy(node: Node) {
    if (node.hasAttr(REFERRER_POLICY_ATTRIBUTE)) {
      node.removeAttr(REFERRER_POLICY_ATTRIBUTE)
    }
  }

  /** A document's `<meta name="referrer">` would replace the page's policy, and renders nothing anyway. */
  private fun shouldSkipNode(node: Node): Boolean {
    return node.nodeName() == "meta" && node.attr("name").equals("referrer", ignoreCase = true)
  }

  private fun traverse(node: Node) {
    ProgressManager.checkCanceled()
    when (node) {
      is TextNode -> textElement { node.wholeText }
      is DataNode -> textElement { node.wholeData }
      is Comment -> Unit
      else -> {
        if (shouldSkipNode(node)) {
          return
        }
        val preprocessed = preprocessNode(node)
        openTag(preprocessed)
        for (child in preprocessed.childNodes()) {
          traverse(child)
        }
        closeTag(preprocessed)
      }
    }
  }
}

private const val REFERRER_POLICY_ATTRIBUTE = "referrerpolicy"

// https://jsoup.org/news/release-1.20.1
private fun createSelfClosingSpanAwareParser(): Parser {
  val tags = TagSet.Html()
  val span = tags.valueOf("span", Parser.NamespaceHtml)
  span.set(Tag.SelfClose)
  return Parser.htmlParser().tagSet(tags)
}
