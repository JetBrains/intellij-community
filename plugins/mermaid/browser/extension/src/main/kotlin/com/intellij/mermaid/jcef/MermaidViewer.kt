package com.intellij.mermaid.jcef

import com.intellij.mermaid.jcef.impl.decode
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.parsing.XMLSerializer
import org.w3c.dom.svg.SVGElement

private fun configureLinks() {
  window.addEventListener("click") { event: Event ->
    val element = event.target as? Element ?: return@addEventListener
    val elements = element.parents(withSelf = true)
    val link = elements.firstOrNull { it.tagName.lowercase() == "a" } ?: return@addEventListener
    val target = link.obtainLinkAttributeValue() ?: return@addEventListener
    try {
      console.log(target)
      window.asDynamic().openLink(target)
    } catch (exception: Throwable) {
      console.error(exception)
    }
    event.preventDefault()
  }
}

private fun Element.obtainLinkAttributeValue(): String? {
  return getAttribute("href") ?: getAttribute("xlink:href")
}

fun collectDiagramContent(): String {
  val document = window.document
  val container = document.getElementById("diagram-container") as HTMLElement
  val element = container.firstChild as? SVGElement
  checkNotNull(element) { "Where should be an svg element" }
  val exported = element.cloneNode(true) as Element
  exported.setAttribute("data-ij-mermaid-generated-on-export", "true")
  exported.removeAttribute("data-natural-width")
  val serializer = XMLSerializer()
  return serializer.serializeToString(exported)
}

private sealed interface ViewUpdate {
  data class Content(val content: String) : ViewUpdate

  data class Refit(val block: Element) : ViewUpdate
}

suspend fun viewerMain() {
  configureLinks()
  window.asDynamic()["updateMermaidDiagramContent"] = ::updateView
  window.asDynamic()["collectDiagramContent"] = ::collectDiagramContent
  merge(
    window.updateViewRequests().map { ViewUpdate.Content(it.content) },
    zoomResetRequests().map { ViewUpdate.Refit(it) },
  ).collect { update ->
    when (update) {
      is ViewUpdate.Content -> performViewUpdate(update.content)
      is ViewUpdate.Refit -> refitDiagram(update.block)
    }
  }
}

private suspend fun refitDiagram(block: Element) {
  resetZoom(block)
  val content = lastRenderedContent ?: return
  renderBlock(block, "", content)
}

private var lastRenderedContent: String? = null
private var lastRenderedWidth = -1.0

private suspend fun performViewUpdate(content: String) {
  val document = window.document
  val container = document.getElementById("diagram-container") ?: return
  val width = (container.parentElement ?: container).clientWidth.toDouble()
  val isResize = content == lastRenderedContent
  if (isResize && !shouldRefitAfterResize(container, widened = width > lastRenderedWidth)) {
    lastRenderedWidth = width
    return
  }
  lastRenderedContent = content
  lastRenderedWidth = width
  val centre = container.scrollCentre()
  renderBlock(container, "", content)
  container.restoreScrollCentre(centre)
}

private data class UpdateViewRequest(val content: String)

fun updateView(content: String) {
  val text = decode(content)
  val event = Event("updateDiagramView")
  event.asDynamic().request = UpdateViewRequest(text)
  window.dispatchEvent(event)
}

private fun EventTarget.updateViewRequests(): Flow<UpdateViewRequest> {
  return eventFlowOf<Event>("updateDiagramView").map { it.asDynamic().request as UpdateViewRequest }
}
