package com.intellij.mermaid.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.util.ui.components.BorderLayoutPanel

internal class MermaidDiagramPreviewComponent: BorderLayoutPanel(), Disposable {
  private val browser = createBrowser()

  init {
    Disposer.register(this, browser)
    browser.jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 20)
    addToCenter(browser.component)
  }

  suspend fun load() {
    val url = MermaidPreviewStaticServer.obtainStaticIndexUrl()
    with(browser) {
      waitForPageLoad(url)
    }
  }

  internal class PreviewUpdateException(cause: Exception): IllegalStateException(cause)

  suspend fun update(text: String) {
    val content = PreviewEncodingUtil.encodeContent(text)
    // language=JavaScript
    val code = """window["updateMermaidDiagramContent"]("$content");"""
    try {
      browser.executeCancellableJavaScript(code)
    } catch (exception: JsCallExecutionException) {
      throw PreviewUpdateException(exception)
    }
  }

  override fun dispose() = Unit

  private fun createBrowser(): JBCefBrowser {
    return JBCefBrowser.createBuilder().setOffScreenRendering(true).build()
  }
}
