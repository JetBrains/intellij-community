package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.Mermaid
import com.intellij.mermaid.api.appendTo
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

private fun handleFailedRender(block: HTMLElement, exception: Throwable) {
  val blockParent = block.findParentDivContainer()
  val lastValidRenderResult = nodeToLastValidHtml[blockParent] ?: ""
  // language=HTML
  val html = """<div class="error-text">${exception.message}</div>$lastValidRenderResult"""
  block.innerHTML = html
  block.findSvgElement()?.setAttribute("opacity", "50%")
}

suspend fun renderBlock(
  block: HTMLElement,
  cacheId: String,
  content: String
): Node? {
  val id = "mermaid-generated-$cacheId"
  try {
    // Remove when `mermaid.render` will throw correct error messages
    Mermaid.core.parse(content).await()

    val renderResult = Mermaid.core.render(id, content).await()
    renderResult.appendTo(block)
    val node = block.findSvgElement()
    checkNotNull(node) { "Failed to find svg node after append" }

    node.updatePieDiagramViewBox()
    block.findParentDivContainer().addStyleAttributeFromElement(node)

    nodeToLastValidHtml[block.findParentDivContainer()] = block.innerHTML
    return node
  } catch (exception: Throwable) {
    console.error("Error while generating blocks:\n", exception)
    handleFailedRender(block, exception)
  }
  return null
}

private fun HTMLElement.findSvgElement(): Element? {
  return findChildElement { it.nodeName == "svg" }
}

private fun Element.findParentDivContainer(): Element {
  val parentElement = parentElement
  checkNotNull(parentElement)
  check(parentElement.nodeName == "DIV")
  return parentElement
}

private fun Element.updatePieDiagramViewBox() {
  if (getAttribute("aria-roledescription") != "pie") return

  val childElement = findChildElement { it.nodeName == "g" && it.hasAttribute("transform") } ?: return

  removeAttribute("viewBox")
  val rect = childElement.getBoundingClientRect()
  setAttribute("viewBox", "0 0 ${rect.right} ${rect.bottom}")
}

private fun Element.addStyleAttributeFromElement(svgElement: Element) {
  val styleValue = svgElement.getAttribute("style") ?: return
  setAttribute("style", styleValue)
}

private fun Element.findChildElement(predicate: (Element) -> Boolean): Element? {
  return childNodes.asList().filterIsInstance<Element>().firstOrNull(predicate)
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
