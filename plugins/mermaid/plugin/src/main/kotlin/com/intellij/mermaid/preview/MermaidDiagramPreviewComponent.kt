package com.intellij.mermaid.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.JBCefOSRHandlerFactory
import com.intellij.util.Function
import com.intellij.util.application
import com.intellij.util.ui.components.BorderLayoutPanel
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import java.awt.Rectangle
import javax.swing.JComponent

class MermaidDiagramPreviewComponent(private val project: Project): BorderLayoutPanel(), Disposable {
  internal val browser = createBrowser()
  private val openLinkQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

  init {
    Disposer.register(this, openLinkQuery)
    Disposer.register(this, browser)
    addToCenter(browser.component)
    openLinkQuery.addVoidHandler { link ->
      link?.let(::openLink)
    }
  }

  suspend fun load() {
    val url = MermaidPreviewStaticServer.obtainStaticIndexUrl()
    with(browser) {
      waitForPageLoad(url)
      injectLinkOpener()
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
    val client = JBCefApp.getInstance().createClient().apply {
      setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 20)
    }
    val builder = JBCefBrowser.createBuilder().apply {
      // Use fixed resolution in tests
      if (application.isUnitTestMode) {
        setOSRHandlerFactory(object: JBCefOSRHandlerFactory {
          override fun createScreenBoundsProvider(): Function<in JComponent, out Rectangle> {
            return Function { Rectangle(1920, 1080) }
          }
        })
      }
      setOffScreenRendering(true)
      setCreateImmediately(true)
      setClient(client)
    }
    return builder.build()
  }

  private suspend fun injectLinkOpener() {
    // language=JavaScript
    val code = """
    window["openLink"] = function(link) {
      window.${openLinkQuery.funcName}({
        request: "" + link,
        onSuccess: (response) => {}, 
        onFailure: (errorCode, errorMessage) => {}
      });
    };
    """.trimIndent()
    browser.executeCancellableJavaScript(code)
  }

  private fun openLink(link: String) {
    @Suppress("UnstableApiUsage")
    MarkdownLinkOpener.getInstance().openLink(project, link)
  }
}
