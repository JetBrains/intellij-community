package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.Mermaid
import com.intellij.mermaid.api.MermaidRenderResult
import com.intellij.mermaid.api.appendTo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.asList

val nodeToLastValidHtml = mutableMapOf<Element, String>()

private fun handleFailedRender(block: Element, exception: Throwable) {
  val lastValidRenderResult = nodeToLastValidHtml[block] ?: ""
  block.innerHTML = lastValidRenderResult

  // language=HTML
  val html = """<div class="error-text">${exception.message}</div>$lastValidRenderResult"""
  block.innerHTML = html

  block.findSvgElement()?.setAttribute("opacity", "50%")
}

suspend fun renderBlock(
  block: Element,
  cacheId: String,
  content: String
): MermaidRenderResult? {
  if (content.isBlank() && nodeToLastValidHtml[block] == null) return null

  val id = "mermaid-generated-$cacheId"
  try {
    // Never cancel a render mid-flight: mermaid uses shared global state and returns a promise that
    // can't be aborted, so an overlapping follow-up render (see markdownExtensionMain) would corrupt it.
    val renderResult = withContext(NonCancellable) {
      // Remove when `mermaid.render` will throw correct error messages
      Mermaid.core.parse(content).await()
      Mermaid.core.render(id, content).await()
    }
    renderResult.appendTo(block)
    val node = block.findSvgElement()
    checkNotNull(node) { "Failed to find svg node after append" }

    node.updatePieDiagramViewBox()
    node.convertExplicitHeightAndWidthAttributesToStyle()
    node.recordNaturalWidth()

    nodeToLastValidHtml[block] = block.innerHTML
    renderResult.svg = block.innerHTML
    applyZoom(block)
    return renderResult
  } catch (exception: Throwable) {
    console.error("Error while generating blocks:\n", exception)
    handleFailedRender(block, exception)
  }
  return null
}

internal fun Element.findSvgElement(): Element? {
  return findChildElement { it.nodeName == "svg" }
}

private fun Element.convertExplicitHeightAndWidthAttributesToStyle() {
  removeAttribute("height")
  if (hasAttribute("style")) return

  val width = getAttribute("width")?.toDoubleOrNull() ?: return
  setAttribute("width", "100%")

  setAttribute("style", "max-width: ${width}px;")
}

private val maxWidthPx = Regex("""max-width:\s*([\d.]+)px""")

private fun Element.recordNaturalWidth() {
  val width = findNaturalWidth()
  if (width == null) {
    console.warn("Could not determine the natural width of a mermaid diagram, so it will not be zoomable", this)
    return
  }
  setAttribute("data-natural-width", width.toString())
}

private fun Element.findNaturalWidth(): Double? {
  getAttribute("style")?.let { maxWidthPx.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }?.let { return it }
  getAttribute("width")?.toDoubleOrNull()?.let { return it }
  getAttribute("viewBox")?.split(' ', ',')?.filter(String::isNotBlank)?.getOrNull(2)?.toDoubleOrNull()?.let { return it }
  return getBoundingClientRect().width.takeIf { it > 0.0 }
}

private fun Element.updatePieDiagramViewBox() {
  if (getAttribute("aria-roledescription") != "pie") return

  val childElement = findChildElement { it.nodeName == "g" && it.hasAttribute("transform") } ?: return

  val height = getAttribute("viewBox")?.split(" ")?.lastOrNull()
  removeAttribute("viewBox")
  val rect = childElement.getBoundingClientRect()
  val origin = getBoundingClientRect()
  setAttribute("viewBox", "0 0 ${rect.right - origin.left} ${height ?: rect.bottom - origin.top}")
}

private fun Element.findChildElement(predicate: (Element) -> Boolean): Element? {
  return childNodes.asList().filterIsInstance<Element>().firstOrNull(predicate)
}
