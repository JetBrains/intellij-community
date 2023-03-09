package com.intellij.mermaid.jcef

import com.intellij.mermaid.jcef.impl.decode
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

suspend fun viewerMain() {
  window.asDynamic()["updateMermaidDiagramContent"] = ::updateView
  window.updateViewRequests().onEach { performViewUpdate(it.content) }.collect()
}

private suspend fun performViewUpdate(content: String) {
  val document = window.document
  val container = document.getElementById("diagram-container") as HTMLElement
  renderBlock(container, "", content)
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
