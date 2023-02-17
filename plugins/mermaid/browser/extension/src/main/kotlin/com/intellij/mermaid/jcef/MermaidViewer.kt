package com.intellij.mermaid.jcef

import com.intellij.mermaid.jcef.impl.decode
import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window
import org.w3c.dom.events.Event

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

private fun Window.updateViewRequests(): Flow<UpdateViewRequest> {
  return callbackFlow {
    addEventListener("updateDiagramView") { event ->
      val request = event.asDynamic().request as UpdateViewRequest
      trySend(request)
    }
    awaitCancellation()
  }
}
