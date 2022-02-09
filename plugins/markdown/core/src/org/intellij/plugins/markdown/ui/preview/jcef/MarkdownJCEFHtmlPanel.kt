// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.ui.preview.*
import org.intellij.plugins.markdown.ui.preview.jcef.impl.FileSchemeResourcesProcessor
import org.intellij.plugins.markdown.ui.preview.jcef.impl.IncrementalDOMBuilder
import org.intellij.plugins.markdown.ui.preview.jcef.impl.JcefBrowserPipeImpl
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

class MarkdownJCEFHtmlPanel(
  private val _project: Project?,
  private val _virtualFile: VirtualFile?
): JCEFHtmlPanel(isOffScreenRendering(), null, null), MarkdownHtmlPanelEx {
  constructor(): this(null, null)

  private val pageBaseName = "markdown-preview-index-${hashCode()}.html"
  private val fileSchemeResourcesProcessor = FileSchemeResourcesProcessor()
  private val resourceProvider = MyAggregatingResourceProvider()
  private val browserPipe = JcefBrowserPipeImpl(
    this,
    injectionAllowedUrls = listOf(PreviewStaticServer.getStaticUrl(resourceProvider, pageBaseName))
  )

  private val scrollListeners = ArrayList<MarkdownHtmlPanel.ScrollListener>()

  private var currentExtensions = emptyList<MarkdownBrowserPreviewExtension>()

  private fun reloadExtensions() {
    currentExtensions.forEach(Disposer::dispose)
    currentExtensions = MarkdownBrowserPreviewExtension.Provider.all
      .mapNotNull { it.createBrowserExtension(this) }
      .filter { (it as? MarkdownConfigurableExtension)?.isEnabled ?: true }
      .sorted()
  }

  private val scripts
    get() = baseScripts + currentExtensions.flatMap { it.scripts }

  private val styles
    get() = currentExtensions.flatMap { it.styles }

  private val scriptingLines
    get() = scripts.joinToString("\n") {
      "<script src=\"${PreviewStaticServer.getStaticUrl(resourceProvider, it)}\"></script>"
    }

  private val stylesLines
    get() = styles.joinToString("\n") {
      "<link rel=\"stylesheet\" href=\"${PreviewStaticServer.getStaticUrl(resourceProvider, it)}\"/>"
    }

  private val contentSecurityPolicy get() =
    PreviewStaticServer.createCSP(
      scripts.map { PreviewStaticServer.getStaticUrl(resourceProvider, it) },
      styles.map { PreviewStaticServer.getStaticUrl(resourceProvider, it) }
    )

  private fun buildIndexContent(): String {
    // language=HTML
    return """
      <!DOCTYPE html>
      <html>
        <head>
          <title>IntelliJ Markdown Preview</title>
          <meta http-equiv="Content-Security-Policy" content="$contentSecurityPolicy"/>
          <meta name="markdown-position-attribute-name" content="${HtmlGenerator.SRC_ATTRIBUTE_NAME}"/>
          $scriptingLines
          $stylesLines
        </head>
      </html>
    """
  }

  private fun loadIndexContent() {
    reloadExtensions()
    loadURL(PreviewStaticServer.getStaticUrl(resourceProvider, pageBaseName))
  }

  @Volatile
  private var delayedContent: String? = null
  private var firstUpdate = true
  private var previousRenderClosure: String = ""

  init {
    Disposer.register(browserPipe) { currentExtensions.forEach(Disposer::dispose) }
    Disposer.register(this, browserPipe)
    Disposer.register(this, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))
    Disposer.register(this, PreviewStaticServer.instance.registerResourceProvider(fileSchemeResourcesProcessor))
    jbCefClient.addRequestHandler(MyFilteringRequestHandler(), cefBrowser)
    browserPipe.subscribe(JcefBrowserPipeImpl.WINDOW_READY_EVENT) {
      delayedContent?.let {
        cefBrowser.executeJavaScript(it, null, 0)
        delayedContent = null
      }
    }
    browserPipe.subscribe(SET_SCROLL_EVENT) { data ->
      data.toIntOrNull()?.let { offset ->
        scrollListeners.forEach { it.onScroll(offset) }
      }
    }
    loadIndexContent()
  }

  private fun updateDom(renderClosure: String, initialScrollOffset: Int) {
    previousRenderClosure = renderClosure
    val scrollCode = if (firstUpdate) {
      "window.scrollController.scrollTo($initialScrollOffset, true);"
    }
    else ""
    val code =
      // language=JavaScript
      """
        (function() {
          const action = () => {
            console.time("incremental-dom-patch");
            const render = $previousRenderClosure;
            // noinspection JSCheckFunctionSignatures
            IncrementalDOM.patch(document.body, () => render());
            $scrollCode
            if (IncrementalDOM.notifications.afterPatchListeners) {
              IncrementalDOM.notifications.afterPatchListeners.forEach(listener => listener());
            }
            console.timeEnd("incremental-dom-patch");
          };
          if (document.readyState === "loading" || document.readyState === "uninitialized") {
            document.addEventListener("DOMContentLoaded", () => action(), { once: true });
          }
          else {
            action();
          }
        })();
      """
    delayedContent = code
    cefBrowser.executeJavaScript(code, null, 0)
  }

  override fun setHtml(html: String, initialScrollOffset: Int, documentPath: Path?) {
    val basePath = documentPath?.parent
    fileSchemeResourcesProcessor.clear()
    val builder = IncrementalDOMBuilder(html, basePath, fileSchemeResourcesProcessor)
    updateDom(builder.generateRenderClosure(), initialScrollOffset)
    firstUpdate = false
  }

  override fun reloadWithOffset(offset: Int) {
    delayedContent = null
    firstUpdate = true
    loadIndexContent()
    updateDom(previousRenderClosure, offset)
  }

  @ApiStatus.Experimental
  override fun getBrowserPipe(): BrowserPipe = browserPipe

  @ApiStatus.Experimental
  override fun getProject(): Project? = _project

  @ApiStatus.Experimental
  override fun getVirtualFile(): VirtualFile? = _virtualFile

  override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
    scrollListeners.add(listener)
  }

  override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener?) {
    scrollListeners.remove(listener)
  }

  override fun scrollToMarkdownSrcOffset(offset: Int, smooth: Boolean) {
    cefBrowser.executeJavaScript(
      // language=JavaScript
      "if (window.scrollController) { window.scrollController.scrollTo($offset, $smooth); }",
      null,
      0
    )
  }

  override fun scrollBy(horizontalUnits: Int, verticalUnits: Int) {
    val horizontal = JBCefApp.normalizeScaledSize(horizontalUnits)
    val vertical = JBCefApp.normalizeScaledSize(verticalUnits)
    cefBrowser.executeJavaScript(
      // language=JavaScript
      "window.scrollBy($horizontal, $vertical)",
      null,
      0
    )
  }

  private inner class MyAggregatingResourceProvider : ResourceProvider {
    private val internalResources = baseScripts + baseStyles

    override fun canProvide(resourceName: String): Boolean {
      return resourceName in internalResources ||
             resourceName == pageBaseName ||
             currentExtensions.any { it.resourceProvider.canProvide(resourceName) }
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
      return when (resourceName) {
        pageBaseName -> ResourceProvider.Resource(buildIndexContent().toByteArray(), "text/html")
        in internalResources -> ResourceProvider.loadInternalResource<MarkdownJCEFHtmlPanel>(resourceName)
        else -> currentExtensions.map { it.resourceProvider }.firstOrNull { it.canProvide(resourceName) }?.loadResource(resourceName)
      }
    }
  }

  private inner class MyFilteringRequestHandler: CefRequestHandlerAdapter() {
    override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, user_gesture: Boolean, is_redirect: Boolean): Boolean {
      if (request.resourceType == CefRequest.ResourceType.RT_CSP_REPORT) {
        logger.warn("""
          Detected a CSP violation on the preview page: $pageBaseName!
          Current page url: ${browser.url}
          Initiated by user gesture: $user_gesture
          Was redirect: $is_redirect
          Full request:
          $request
        """.trimIndent())
        return true
      }
      val targetPageUrl = PreviewStaticServer.getStaticUrl(resourceProvider, pageBaseName)
      val requestedUrl = request.url
      if (requestedUrl != targetPageUrl) {
        logger.warn("""
          Canceling request for an external page with url: $requestedUrl.
          Current page url: ${browser.url}
          Target safe url: $targetPageUrl
        """.trimIndent())
        return true
      }
      return false
    }

    override fun onOpenURLFromTab(browser: CefBrowser, frame: CefFrame, target_url: String, user_gesture: Boolean): Boolean {
      logger.warn("Canceling navigation for url: $target_url (user_gesture=$user_gesture)")
      return true
    }
  }

  companion object {
    private val logger = logger<MarkdownJCEFHtmlPanel>()

    private const val SET_SCROLL_EVENT = "setScroll"

    private val baseScripts = listOf(
      "incremental-dom.min.js",
      "incremental-dom-additions.js",
      "BrowserPipe.js",
      "ScrollSync.js"
    )

    private val baseStyles = emptyList<String>()

    private fun isOffScreenRendering(): Boolean = Registry.`is`("ide.browser.jcef.markdownView.osr.enabled")
  }
}
