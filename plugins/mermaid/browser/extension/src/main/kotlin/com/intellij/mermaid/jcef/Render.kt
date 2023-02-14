package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.Mermaid
import com.intellij.mermaid.api.MermaidApi
import com.intellij.mermaid.api.renderAsync
import com.intellij.mermaid.jcef.impl.decode
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.asList
import org.w3c.dom.parsing.XMLSerializer

val nodeToLastValidHtml = mutableMapOf<Element, String>()

private suspend fun MermaidApi.render(id: String, text: String): Result<String?> {
  return runCatching {
    Mermaid.api.renderAsync(id, text).await()
  }
}

private fun handleFailedRender(block: HTMLElement, exception: Throwable) {
  val blockParent = block.findCodeBlockContainer()
  val lastValidRenderResult = nodeToLastValidHtml[blockParent] ?: ""
  // language=HTML
  val html = """<div class="error-text">${exception.message}</div>$lastValidRenderResult"""
  block.innerHTML = html
  block.findSvgElement()?.setAttribute("opacity", "50%")
}

suspend fun renderBlock(block: HTMLElement, cacheId: String, content: String): Node? {
  val id = "mermaid-generated-$cacheId"
  val renderResult = Mermaid.api.render(id, content)
  val svg = renderResult.getOrNull()
  if (svg == null) {
    val exception = renderResult.exceptionOrNull()
    if (exception == null) {
      console.error("Failed to generate block without exception for\n$content")
      return null
    }
    console.error("Error while generating blocks:\n", exception)
    handleFailedRender(block, exception)
    return null
  }
  block.innerHTML = svg
  val node = block.findSvgElement()
  checkNotNull(node) { "Failed to find svg node after append" }
  addExplicitDimensionsAttributes(node.unsafeCast<HTMLElement>())
  nodeToLastValidHtml[block.findCodeBlockContainer()] = block.innerHTML
  return node
}

private fun HTMLElement.findSvgElement(): Element? {
  return childNodes.asList().filterIsInstance<Element>().firstOrNull { it.nodeName == "svg" }
}

private fun HTMLElement.findCodeBlockContainer(): Element {
  val parentElement = parentElement
  checkNotNull(parentElement)
  check(parentElement.className == "language-mermaid")
  return parentElement
}

private fun addExplicitDimensionsAttributes(element: HTMLElement) {
  val rect = element.getBoundingClientRect()
  val width = rect.width
  val height = rect.height
  element.apply {
    setAttribute("width", "${width}px")
    setAttribute("height", "${height}px")
  }
}

fun cacheBlock(cacheId: String, block: Node) {
  val serializer = XMLSerializer()
  val serialized = serializer.serializeToString(block)
  val data = "$cacheId;$serialized"
  window.obtainMessagePipe()?.post("storeMermaidFile", data)
}

suspend fun processBlock(block: HTMLElement): Node? {
  block.removeAttribute("data-processed")
  val cacheId = block.getAttribute("data-cache-id")
  checkNotNull(cacheId) { "data-cache-id was not set for block" }
  val content = block.getAttribute("data-actual-fence-content")
  checkNotNull(content) { "data-actual-fence-content was not set for block" }
  val actualContent = decode(content)
  val generated = renderBlock(block, cacheId, actualContent) ?: return null
  cacheBlock(cacheId, generated)
  return generated
}

suspend fun renderBlocks(blocks: List<HTMLElement>) {
  coroutineScope {
    for (block in blocks) {
      // renderAsync for some reason depends on some global state,
      // so it is not possible to run it in parallel
      processBlock(block)
    }
  }
}
