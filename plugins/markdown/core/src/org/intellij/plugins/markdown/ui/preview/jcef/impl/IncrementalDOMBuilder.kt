// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef.impl

import com.intellij.openapi.diagnostic.thisLogger
import org.intellij.plugins.markdown.ui.preview.html.links.IntelliJImageGeneratingProvider
import org.jetbrains.annotations.ApiStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
class IncrementalDOMBuilder(
  html: String,
  private val basePath: Path?,
  private val fileSchemeResourceProcessor: FileSchemeResourceProcessingStrategy? = null
) {
  fun interface FileSchemeResourceProcessingStrategy {
    fun processFileSchemeResource(basePath: Path, originalUri: URI): String?
  }

  private val document = Jsoup.parse(html)
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
    return URLEncoder.encode(argument, Charsets.UTF_8).replace("+", "%20")
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

  private fun preprocessNode(node: Node): Node {
    if (fileSchemeResourceProcessor != null && basePath != null && shouldPreprocessImageNode(node)) {
      try {
        actuallyProcessImageNode(node, basePath, fileSchemeResourceProcessor)
      } catch (exception: Throwable) {
        val originalUrlValue = node.attr("src")
        thisLogger().error("Failed to process image node\nbasePath: $basePath\noriginalUrl: $originalUrlValue", exception)
      }
    }
    return node
  }

  private fun actuallyProcessImageNode(node: Node, basePath: Path, fileSchemeResourceProcessor: FileSchemeResourceProcessingStrategy) {
    val originalUrlValue = node.attr("src")
    val uri = when {
      originalUrlValue.startsWith("file:/") -> createUri(originalUrlValue)
      else -> createFileUri(originalUrlValue, basePath) ?: createUri(originalUrlValue)
    }
    if (uri != null && (uri.scheme == null || uri.scheme == "file")) {
      val processed = fileSchemeResourceProcessor.processFileSchemeResource(basePath, uri) ?: return
      node.attr("data-original-src", originalUrlValue)
      node.attr("src", processed)
    }
  }

  private fun shouldPreprocessImageNode(node: Node): Boolean {
    return node.nodeName() == "img" && !node.hasAttr(IntelliJImageGeneratingProvider.ignorePathProcessingAttributeName)
  }

  private fun traverse(node: Node) {
    when (node) {
      is TextNode -> textElement { node.wholeText }
      is DataNode -> textElement { node.wholeData }
      is Comment -> Unit
      else -> {
        val preprocessed = preprocessNode(node)
        openTag(preprocessed)
        for (child in preprocessed.childNodes()) {
          traverse(child)
        }
        closeTag(preprocessed)
      }
    }
  }

  companion object {
    private fun createUri(string: String): URI? {
      try {
        return URI(string)
      } catch (exception: URISyntaxException) {
        return null
      }
    }

    private fun createFileUri(string: String, basePath: Path?): URI? {
      try {
        val resolved = basePath?.resolve(string) ?: Paths.get(string)
        return resolved.toUri()
      } catch(ignored: Throwable) {
        return null
      }
    }
  }
}
