// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.application
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.ui.actions.changeFontSize
import org.intellij.plugins.markdown.ui.preview.*
import org.intellij.plugins.markdown.ui.preview.jcef.impl.*
import org.intellij.plugins.markdown.util.MarkdownApplicationScope
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.net.URL
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

class MarkdownJCEFHtmlPanel(
  private val project: Project?,
  private val virtualFile: VirtualFile?
) : JCEFHtmlPanel(isOffScreenRendering(), null, null), MarkdownHtmlPanelEx, UserDataHolder by UserDataHolderBase() {
  constructor() : this(null, null)

  private val pageBaseName = "markdown-preview-index-${hashCode()}.html"

  private val resourceProvider = MyAggregatingResourceProvider()
  private val browserPipe: BrowserPipe = JcefBrowserPipeImpl(
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

  private val contentSecurityPolicy get() = PreviewStaticServer.createCSP(
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

  private suspend fun loadIndexContent() {
    reloadExtensions()
    waitForPageLoad(PreviewStaticServer.getStaticUrl(resourceProvider, pageBaseName))
  }

  private var previousRenderClosure: String = ""

  private val coroutineScope = project?.let(MarkdownPluginScope::createChildScope) ?: MarkdownApplicationScope.createChildScope()

  private sealed interface PreviewRequest {
    data class Update(
      val content: String,
      val initialScrollOffset: Int,
      val document: VirtualFile?
    ) : PreviewRequest

    data class ReloadWithOffset(val offset: Int) : PreviewRequest
  }

  private val updateViewRequests = MutableSharedFlow<PreviewRequest>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val projectRoot = coroutineScope.async(context = Dispatchers.Default) {
    if (virtualFile != null && project != null) {
      readAction { ProjectFileIndex.getInstance(project).getContentRootForFile(virtualFile) }
    } else null
  }

  private val panelComponent by lazy { createComponent() }

  override fun getComponent(): JComponent {
    return panelComponent
  }

  init {
    Disposer.register(browserPipe) { currentExtensions.forEach(Disposer::dispose) }
    Disposer.register(this, browserPipe)
    Disposer.register(this, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))

    jbCefClient.addRequestHandler(MyFilteringRequestHandler(), cefBrowser, this)
    jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 20)

    browserPipe.subscribe(SET_SCROLL_EVENT, object : BrowserPipe.Handler {
      override fun processMessageReceived(data: String): Boolean {
        data.toIntOrNull()?.let { offset -> scrollListeners.forEach { it.onScroll(offset) } }
        return false
      }
    })
    val connection = application.messageBus.connect(this)
    connection.subscribe(MarkdownPreviewSettings.ChangeListener.TOPIC, MarkdownPreviewSettings.ChangeListener { settings ->
      changeFontSize(settings.state.fontSize)
    })

    coroutineScope.launch {
      val projectRoot = projectRoot.await()
      val fileSchemeResourcesProcessor = createFileSchemeResourcesProcessor(projectRoot)

      loadIndexContent()
      updateViewRequests.debounce(20.milliseconds).collectLatest { request ->
        when (request) {
          is PreviewRequest.Update -> {
            val (html, initialScrollOffset, document) = request
            val baseFile = document?.parent
            val builder = IncrementalDOMBuilder(html, baseFile, projectRoot, fileSchemeResourcesProcessor)
            val renderClosure = builder.generateRenderClosure()
            updateDom(renderClosure, initialScrollOffset, previousRenderClosure.isEmpty())
          }
          is PreviewRequest.ReloadWithOffset -> {
            loadIndexContent()
            updateDom(previousRenderClosure, request.offset, firstUpdate = true)
          }
        }
      }
    }
  }

  private suspend fun updateDom(renderClosure: String, initialScrollOffset: Int, firstUpdate: Boolean) {
    previousRenderClosure = renderClosure
    // language=JavaScript
    val scrollCode = when {
      firstUpdate -> "window.scrollController?.scrollTo($initialScrollOffset, true);"
      else -> ""
    }
    // language=JavaScript
    val code = """
      (function() {
        return new Promise( resolve => {
          const action = () => {
            console.time("incremental-dom-patch");
            const render = $renderClosure;
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
          } else {
            action();
          }
          resolve();
        });
      })();
    """.trimIndent()
    executeCancellableJavaScript(code)
  }

  override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) {
    check(updateViewRequests.tryEmit(PreviewRequest.Update(content = html, initialScrollOffset, document)))
  }

  @ApiStatus.Internal
  @TestOnly
  suspend fun setHtmlAndWait(html: String) {
    loadIndexContent()

    val builder = IncrementalDOMBuilder(html, null, null, null)
    val renderClosure = readAction { builder.generateRenderClosure() }
    updateDom(renderClosure, 0, false)
  }

  override fun reloadWithOffset(offset: Int) {
    check(updateViewRequests.tryEmit(PreviewRequest.ReloadWithOffset(offset)))
  }

  override fun dispose() {
    for (extension in currentExtensions) {
      Disposer.dispose(extension)
    }
    currentExtensions = emptyList()
    scrollListeners.clear()
    coroutineScope.cancel()
    super.dispose()
  }

  @ApiStatus.Experimental
  override fun getBrowserPipe(): BrowserPipe = browserPipe

  @ApiStatus.Experimental
  override fun getProject(): Project? = project

  @ApiStatus.Experimental
  override fun getVirtualFile(): VirtualFile? = virtualFile

  override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
    scrollListeners.add(listener)
  }

  override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
    scrollListeners.remove(listener)
  }

  override fun scrollToMarkdownSrcOffset(offset: Int, smooth: Boolean) {
    executeJavaScript("window.scrollController?.scrollTo($offset, $smooth)")
  }

  override fun scrollBy(horizontalUnits: Int, verticalUnits: Int) {
    val horizontal = JBCefApp.normalizeScaledSize(horizontalUnits)
    val vertical = JBCefApp.normalizeScaledSize(verticalUnits)
    executeJavaScript("window.scrollController?.scrollBy($horizontal, $vertical)")
  }

  private fun createComponent(): JComponent {
    val component = super.getComponent()
    if (project == null || virtualFile == null) return component

    val panel = JBLoadingPanel(BorderLayout(), this)
    coroutineScope.async(context = Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      panel.startLoading()
      panel.add(component)
      projectRoot.await()
      panel.stopLoading()
    }
    return panel
  }

  private fun createFileSchemeResourcesProcessor(projectRoot: VirtualFile?): ResourceProvider? {
    if (projectRoot == null) return null

    val fileSchemeResourcesProcessor = FileSchemeResourcesProcessor(virtualFile, projectRoot)
    Disposer.register(this@MarkdownJCEFHtmlPanel, PreviewStaticServer.instance.registerResourceProvider(fileSchemeResourcesProcessor))
    return fileSchemeResourcesProcessor
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

  private inner class MyFilteringRequestHandler : CefRequestHandlerAdapter() {
    override fun getResourceRequestHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest, isNavigation: Boolean, isDownload: Boolean, requestInitiator: String?, disableDefaultHandling: BoolRef?): CefResourceRequestHandler? {
      if (Registry.`is`("markdown.experimental.allow.external.requests", true)) {
        return null
      }
      val url = runCatching { URL(request.url) }.getOrNull() ?: return null
      if (!NetUtils.isLocalhost(url.host)) {
        return ProhibitingResourceRequestHandler
      }
      return null
    }

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

    private object ProhibitingResourceRequestHandler : CefResourceRequestHandlerAdapter() {
      override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): Boolean {
        return true
      }
    }
  }
}
