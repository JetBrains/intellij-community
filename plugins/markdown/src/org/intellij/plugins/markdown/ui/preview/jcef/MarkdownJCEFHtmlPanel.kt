// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JCEFHtmlPanel
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.jcef.impl.JcefBrowserPipeImpl
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.random.Random

class MarkdownJCEFHtmlPanel(
  private val _project: Project?,
  private val _virtualFile: VirtualFile?
): JCEFHtmlPanel(isOffScreenRendering(), null, getClassUrl()), MarkdownHtmlPanel {
  constructor(): this(null, null)

  private val resourceProvider = MyResourceProvider()
  private val browserPipe = JcefBrowserPipeImpl(this)

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

  private fun loadIndexContent() {
    reloadExtensions()
    // language=HTML
    val content = """
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
    super<JCEFHtmlPanel>.setHtml(content)
  }

  @Volatile
  private var delayedContent: String? = null
  private var firstUpdate = true
  private var previousRenderClosure: String = ""

  init {
    Disposer.register(browserPipe) { currentExtensions.forEach(Disposer::dispose) }
    Disposer.register(this, browserPipe)
    val resourceProviderRegistration = PreviewStaticServer.instance.registerResourceProvider(resourceProvider)
    Disposer.register(this, resourceProviderRegistration)
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
    val builder = IncrementalDOMBuilder(html, basePath)
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

  private inner class MyResourceProvider : ResourceProvider {
    private val internalResources = baseScripts + baseStyles

    override fun canProvide(resourceName: String): Boolean {
      return resourceName in internalResources || currentExtensions.any { it.resourceProvider.canProvide(resourceName) }
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
      return when (resourceName) {
        in internalResources -> ResourceProvider.loadInternalResource<MarkdownJCEFHtmlPanel>(resourceName)
        else -> currentExtensions.map { it.resourceProvider }.firstOrNull { it.canProvide(resourceName) }?.loadResource(resourceName)
      }
    }
  }

  companion object {
    private const val SET_SCROLL_EVENT = "setScroll"

    private val baseScripts = listOf(
      "incremental-dom.min.js",
      "incremental-dom-additions.js",
      "BrowserPipe.js",
      "ScrollSync.js"
    )

    private val baseStyles = emptyList<String>()

    private fun getClassUrl(): String {
      val url = try {
        val cls = MarkdownJCEFHtmlPanel::class.java
        cls.getResource("${cls.simpleName}.class")?.toExternalForm() ?: error("Failed to get class URL!")
      } catch (ignored: Exception) {
        "about:blank"
      }
      return "$url@${Random.nextInt(Integer.MAX_VALUE)}"
    }

    private fun isOffScreenRendering(): Boolean = Registry.`is`("ide.browser.jcef.markdownView.osr.enabled")
  }
}
