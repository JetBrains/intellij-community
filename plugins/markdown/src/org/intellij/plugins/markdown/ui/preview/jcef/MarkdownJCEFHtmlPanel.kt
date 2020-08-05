// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownAccessor
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import kotlin.random.Random

class MarkdownJCEFHtmlPanel : JCEFHtmlPanel(getClassUrl()), MarkdownHtmlPanel {
  private val resourceProvider = MyResourceProvider()
  private val browserPipe = BrowserPipe(this)

  private val scrollQuery = requireNotNull(JBCefJSQuery.create(this)) {
    "Could not create scroll query!"
  }

  private val scrollListener = ScrollPreservingListener()

  init {
    Disposer.register(this, browserPipe)
    Disposer.register(this, scrollQuery)
    PreviewStaticServer.instance.resourceProvider = resourceProvider
    extensions.flatMap {
      it.events.entries
    }.forEach { (event, handler) ->
      browserPipe.addBrowserEvents(event)
      browserPipe.subscribe(event, handler)
    }
    scrollQuery.addHandler {
      try {
        scrollListener.scrollValue = Integer.parseInt(it)
      }
      catch (ignored: NumberFormatException) {}
      null
    }
    jbCefClient.addLoadHandler(scrollListener, cefBrowser)
  }

  override fun scrollToMarkdownSrcOffset(offset: Int) {
    val code =
      // language=JavaScript
      """
        (function() {
          const scrollAction = () => {
            __IntelliJTools.scrollToOffset($offset, '${HtmlGenerator.SRC_ATTRIBUTE_NAME}');
            let value = document.documentElement.scrollTop || (document.body && document.body.scrollTop);
            ${scrollQuery.inject("value")}
          };
          if ('__IntelliJTools' in window) {
            scrollAction();
          }
          else {
            window.addEventListener('load', scrollAction);
          }
        })();
      """.trimIndent()
    cefBrowser.executeJavaScript(code, cefBrowser.url, 0)
  }

  override fun prepareHtml(html: String): String {
    return MarkdownAccessor.getImageRefreshFixAccessor().setStamps(
      html.replace(
        "<head>",
        // language=HTML
        """
          <head>
          <meta http-equiv="Content-Security-Policy" content="$contentSecurityPolicy"/>
          $stylesLines
          $scriptingLines
        """.trimIndent()
      )
    )
  }

  override fun dispose() {
    super.dispose()
    jbCefClient.removeLoadHandler(scrollListener, cefBrowser)
  }

  private inner class ScrollPreservingListener: CefLoadHandlerAdapter() {
    var scrollValue = 0

    override fun onLoadingStateChange(
      browser: CefBrowser?,
      isLoading: Boolean,
      canGoBack: Boolean,
      canGoForward: Boolean
    ) {
      val code = if (isLoading) {
        // language=JavaScript
        """
          var value = document.documentElement.scrollTop || document.body.scrollTop;
          ${scrollQuery.inject("value")}
        """.trimIndent()
      }
      else {
        "document.documentElement.scrollTop = ({} || document.body).scrollTop = $scrollValue;"
      }
      cefBrowser.executeJavaScript(code, cefBrowser.url, 0)
    }
  }

  private inner class MyResourceProvider : ResourceProvider {
    private val preloadScript by lazy { browserPipe.inject().toByteArray() }

    override fun canProvide(resourceName: String): Boolean {
      return resourceName in baseScripts ||
             extensions.any { it.resourceProvider.canProvide(resourceName) }
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
      if (resourceName in baseScripts) {
        if (resourceName == PRELOAD_SCRIPT_FILENAME) {
          return ResourceProvider.Resource(preloadScript)
        }
        return ResourceProvider.loadInternalResource(MarkdownJCEFHtmlPanel::class, resourceName)
      }
      return extensions.map { it.resourceProvider }.firstOrNull {
        it.canProvide(resourceName)
      }?.loadResource(resourceName)
    }
  }

  companion object {
    private val extensions = MarkdownJCEFPreviewExtension.allSorted

    private const val PRELOAD_SCRIPT_FILENAME = "preload.js"

    private val baseScripts = listOf(
      "scrollToElement.js",
      "browserPipe.js",
      PRELOAD_SCRIPT_FILENAME
    )

    val scripts = baseScripts + extensions.flatMap { it.scripts }
    val styles = extensions.flatMap { it.styles }

    private val scriptingLines by lazy {
      scripts.joinToString("\n") {
        "<script src=\"${PreviewStaticServer.getStaticUrl(it)}\"></script>"
      }
    }

    private val stylesLines by lazy {
      styles.joinToString("\n") {
        "<link rel=\"stylesheet\" href=\"${PreviewStaticServer.getStaticUrl(it)}\"/>"
      }
    }

    private val contentSecurityPolicy by lazy {
      PreviewStaticServer.createCSP(
        scripts.map { PreviewStaticServer.getStaticUrl(it) },
        styles.map { PreviewStaticServer.getStaticUrl(it) }
      )
    }

    private fun getClassUrl(): String {
      val url = try {
        val cls = MarkdownJCEFHtmlPanel::class.java
        cls.getResource("${cls.simpleName}.class").toExternalForm() ?: error("Failed to get class URL!")
      }
      catch (ignored: Exception) {
        "about:blank"
      }
      return "$url@${Random.nextInt(Integer.MAX_VALUE)}"
    }
  }
}
