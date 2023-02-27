package com.intellij.mermaid.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.executeJavaScriptAsync
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.concurrency.await

internal class MermaidDiagramPreviewComponent: BorderLayoutPanel(), Disposable {
  private val browser = JBCefBrowser()

  init {
    Disposer.register(this, browser)
    browser.jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 10)
    addToCenter(browser.component)
  }

  suspend fun load() {
    val url = MermaidPreviewStaticServer.obtainStaticIndexUrl()
    with(browser) {
      component.isVisible = false
      waitForPageLoad(url)
      component.isVisible = true
      // Revalidate, so that JCEF component can resize to the correct size
      component.revalidate()
    }
  }

  suspend fun update(text: String) {
    val content = PreviewEncodingUtil.encodeContent(text)
    // language=JavaScript
    val code = """window["updateMermaidDiagramContent"]("$content");"""
    browser.executeJavaScriptAsync(code).await()
  }

  override fun dispose() = Unit
}
