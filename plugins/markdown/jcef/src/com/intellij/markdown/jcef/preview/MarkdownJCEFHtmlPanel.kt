// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jcef.preview

import com.intellij.find.FindManager
import com.intellij.markdown.frontend.preview.jcef.zoomIndicator.PreviewZoomIndicatorManager
import com.intellij.markdown.jcef.preview.impl.IncrementalDOMBuilder
import com.intellij.markdown.jcef.preview.impl.JcefBrowserPipeImpl
import com.intellij.markdown.jcef.preview.impl.addRequestHandler
import com.intellij.markdown.jcef.preview.impl.executeCancellableJavaScript
import com.intellij.markdown.jcef.preview.impl.waitForPageLoad
import com.intellij.markdown.jcef.preview.impl.waitForReloadIgnoringCache
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.Magnificator
import com.intellij.ui.components.ZoomableViewport
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.application
import com.intellij.util.io.DigestUtil
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelEx
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewBrowserActions
import org.intellij.plugins.markdown.ui.preview.MarkdownUpdateHandler
import org.intellij.plugins.markdown.ui.preview.MarkdownUpdateHandler.PreviewRequest
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles.fontSizeOptions
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.util.MarkdownApplicationScope
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Point
import java.net.URL
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.round

class MarkdownJCEFHtmlPanel(private val project: Project?, private val virtualFile: VirtualFile?) : JCEFHtmlPanel(
  isOffScreenRendering = isOffScreenRendering(),
  client = null,
  url = null,
), MarkdownHtmlPanelEx, UserDataHolder by UserDataHolderBase(), MarkdownPreviewBrowserActions {
  constructor() : this(project = null, virtualFile = null)

  private val pageBaseName = "markdown-preview-index-${DigestUtil.randomToken()}.html"
  private val resourceProvider = MyAggregatingResourceProvider()
  private val pageUrl = PreviewStaticServer.getStaticUrl(resourceProvider, pageBaseName)
  private val browserPipe: BrowserPipe = JcefBrowserPipeImpl(browser = this, injectionAllowedUrls = listOf(pageUrl))

  private val scrollListeners = ArrayList<MarkdownHtmlPanel.ScrollListener>()

  @Suppress("UsagesOfObsoleteApi")
  private var currentExtensions = emptyList<MarkdownBrowserPreviewExtension>()

  @Suppress("UsagesOfObsoleteApi")
  private fun reloadExtensions() {
    currentExtensions.forEach(Disposer::dispose)
    currentExtensions = MarkdownBrowserPreviewExtension.Provider.all
      .mapNotNull { it.createBrowserExtension(this) }
      .filter { (it as? MarkdownConfigurableExtension)?.isEnabled ?: true }
      .sorted()
  }

  private val updateHandler = MarkdownUpdateHandler.Debounced()
  private val initialization = CompletableDeferred<Unit>()

  /** The one served resource meant to be a document: it declares its own policy in a `<meta>`. */
  private fun buildIndexResource(): ResourceProvider.Resource {
    val scripts = (baseScripts + currentExtensions.flatMap { it.scripts }).map { PreviewStaticServer.getStaticUrl(resourceProvider, it) }
    val styles = currentExtensions.flatMap { it.styles }.map { PreviewStaticServer.getStaticUrl(resourceProvider, it) }
    // language=HTML
    val content = """
      <!DOCTYPE html>
      <html>
        <head>
          <title>IntelliJ Markdown Preview</title>
          <meta http-equiv="Content-Security-Policy" content="${PreviewStaticServer.createCSP(scripts, styles)}"/>
          <meta name="referrer" content="no-referrer"/>
          <meta name="markdown-position-attribute-name" content="${HtmlGenerator.SRC_ATTRIBUTE_NAME}"/>
          ${scripts.joinToString("\n") { "<script src=\"${it}\"></script>" }}
          ${styles.joinToString("\n") { "<link rel=\"stylesheet\" href=\"${it}\"/>" }}
        </head>
      </html>
    """
    return ResourceProvider.Resource(content.toByteArray(), "text/html", isDocument = true)
  }

  private suspend fun loadIndexContent() {
    reloadExtensions()
    waitForPageLoad(pageUrl)
  }

  /** Like [loadIndexContent], but bypasses the cache so settings-dependent resources refresh (see [waitForReloadIgnoringCache]). */
  private suspend fun reloadIndexContent() {
    reloadExtensions()
    waitForReloadIgnoringCache()
  }

  private var previousRenderClosure: String = ""

  private val coroutineScope = project?.let(MarkdownPluginScope::createChildScope) ?: MarkdownApplicationScope.createChildScope()


  private val panelComponent by lazy { createComponent() }

  private var searchSession: MarkdownPreviewSearchSession? = null

  override fun showSearchBar() {
    searchSession?.showSearchBar()
  }

  override fun getComponent(): JComponent = panelComponent

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
      try {
        val imageResourceProvider = createImageResourceProvider()

        loadIndexContent()
        initialization.complete(Unit)
        updateHandler.requests.collectLatest { request ->
          try {
            when (request) {
              is PreviewRequest.Update -> {
                val (html, initialScrollOffset, document) = request
                val builder = IncrementalDOMBuilder(html, document, imageResourceProvider)
                val renderClosure = builder.generateRenderClosure()
                updateDom(renderClosure, initialScrollOffset, previousRenderClosure.isEmpty())
              }
              is PreviewRequest.ReloadWithOffset -> {
                reloadIndexContent()
                updateDom(previousRenderClosure, request.offset, firstUpdate = true)
              }
            }
          }
          catch (e: Exception) {
            rethrowControlFlowException(e)
            thisLogger().error(e)
          }
        }
      }
      catch (e: Throwable) {
        initialization.completeExceptionally(e)
        rethrowControlFlowException(e)
        thisLogger().error("Failed to initialize the Markdown preview", e)
        throw e
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
    updateHandler.setContent(html, initialScrollOffset, document)
  }

  @ApiStatus.Internal
  suspend fun setHtmlAndWait(html: String, document: VirtualFile? = null, imageResourceProvider: ResourceProvider? = null) {
    initialization.await()

    val builder = IncrementalDOMBuilder(html, document, imageResourceProvider)
    val renderClosure = readAction { builder.generateRenderClosure() }
    updateDom(renderClosure, 0, false)
  }

  override fun reloadWithOffset(offset: Int) {
    updateHandler.reloadWithOffset(offset)
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

  @Suppress("OVERRIDE_DEPRECATION")
  override fun scrollToMarkdownSrcOffset(offset: Int, smooth: Boolean) {
    runJavaScript("window.scrollController?.scrollTo($offset, $smooth)")
  }

  override fun scrollBy(horizontalUnits: Int, verticalUnits: Int) {
    val horizontal = JBCefApp.normalizeScaledSize(horizontalUnits)
    val vertical = JBCefApp.normalizeScaledSize(verticalUnits)
    runJavaScript("window.scrollController?.scrollBy($horizontal, $vertical)")
  }

  private val previewInnerComponent by lazy { super.getComponent() }
  private val TEMPORARY_FONT_SIZE = Key.create<Int>("Markdown.Preview.FontSize")

  private inner class ViewPort : JPanel(BorderLayout()), ZoomableViewport {
    private var myMagnificationPoint: Point? = null

    override fun getMagnificator(): Magnificator {
      return Magnificator { scale, at ->
        val currentSize = this@MarkdownJCEFHtmlPanel.getTemporaryFontSize() ?: PreviewLAFThemeStyles.defaultFontSize
        var fontSize = round(currentSize * scale).toInt()
        fontSize = maxOf(fontSize, fontSizeOptions.first())
        fontSize = minOf(fontSize, fontSizeOptions.last())
        changeFontSize(fontSize, temporary = true)

        at
      }
    }

    override fun magnificationStarted(at: Point) {
      myMagnificationPoint = at
    }

    override fun magnificationFinished(magnification: Double) {
      myMagnificationPoint = null
    }

    override fun magnify(magnification: Double) {
      if (magnification.compareTo(0.0) != 0 && myMagnificationPoint != null) {
        val magnificator = magnificator
        val step = magnification * 0.1
        val scale = if (magnification < 0) 1 / (1 - step) else 1 + step
        magnificator.magnify(scale, myMagnificationPoint)
      }
    }
  }

  private fun createComponent(): JComponent {
    if (project == null || virtualFile == null) return previewInnerComponent

    val panel = JBLoadingPanel(BorderLayout(), this)

    previewInnerComponent.addPropertyChangeListener(TEMPORARY_FONT_SIZE.toString()) {
      val balloon = project.service<PreviewZoomIndicatorManager>().createOrGetBalloon(this@MarkdownJCEFHtmlPanel)
      balloon?.show(RelativePoint.getSouthOf(previewInnerComponent), Balloon.Position.below)
    }

    // MarkdownPreviewSearchSession requires EDT context to update actions in SearchReplaceComponent.updateBindingsActionsAndFocus
    coroutineScope.launch(context = Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      panel.startLoading()
      // preload it before we hit it on EDT from MarkdownPreviewSearchSession
      val findManager = withContext(Dispatchers.Default) { project.serviceAsync<FindManager>() }

      val session = MarkdownPreviewSearchSession(project, findManager, cefBrowser, previewInnerComponent)
      previewInnerComponent.add(session.getComponent(), BorderLayout.NORTH)
      searchSession = session
      val viewPort = ViewPort()
      viewPort.add(previewInnerComponent, BorderLayout.CENTER)
      panel.add(viewPort)

      panel.stopLoading()
    }

    return panel
  }

  override fun getTemporaryFontSize(): Int? = getUserData(TEMPORARY_FONT_SIZE)

  /**
   * @param size Unscaled font size.
   */
  override fun changeFontSize(size: Int, temporary: Boolean) {
    if (temporary) {
      val previousSize = getUserData(TEMPORARY_FONT_SIZE) ?: PreviewLAFThemeStyles.defaultFontSize
      putUserData(TEMPORARY_FONT_SIZE, size)
      previewInnerComponent.firePropertyChange(TEMPORARY_FONT_SIZE.toString(), previousSize, size)
    }
    else {
      putUserData(TEMPORARY_FONT_SIZE, null)
    }

    val scaled = JBCefApp.normalizeScaledSize(size)
    // language=JavaScript
    val code = """
    |(function() {
    |  const styles = document.querySelector(":root").style;
    |  styles.setProperty("${PreviewLAFThemeStyles.Variables.FontSize}", "${scaled}px");
    |})();
    """.trimMargin()
    runJavaScript(code)
  }

  override fun openDevtools() {
    super.openDevtools()
  }

  @ApiStatus.Internal
  fun createImageResourceProvider(): ResourceProvider {
    val provider = MarkdownImageResourceProvider(project, virtualFile)
    Disposer.register(this@MarkdownJCEFHtmlPanel, PreviewStaticServer.instance.registerResourceProvider(provider))
    return provider
  }

  private inner class MyAggregatingResourceProvider : ResourceProvider {
    private val internalResources = baseScripts + baseStyles

    override fun canProvide(resourceName: String): Boolean =
      resourceName in internalResources ||
      resourceName == pageBaseName ||
      currentExtensions.any { it.resourceProvider.canProvide(resourceName) }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? = when (resourceName) {
      pageBaseName -> buildIndexResource()
      in internalResources -> ResourceProvider.loadInternalResource<MarkdownJCEFHtmlPanel>(resourceName)
      else -> currentExtensions.map { it.resourceProvider }.firstOrNull { it.canProvide(resourceName) }?.loadResource(resourceName)
    }
  }

  private inner class MyFilteringRequestHandler : CefRequestHandlerAdapter() {
    override fun getResourceRequestHandler(
      browser: CefBrowser?,
      frame: CefFrame?,
      request: CefRequest,
      isNavigation: Boolean,
      isDownload: Boolean,
      requestInitiator: String?,
      disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler? {
      if (Registry.`is`("markdown.experimental.allow.external.requests", true)) {
        return null
      }
      val url = runCatching { URL(request.url) }.getOrNull() ?: return null
      if (!NetUtils.isLocalhost(url.host)) {
        return ProhibitingResourceRequestHandler
      }
      return null
    }

    override fun onBeforeBrowse(
      browser: CefBrowser,
      frame: CefFrame,
      request: CefRequest,
      user_gesture: Boolean,
      is_redirect: Boolean,
    ): Boolean {
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
      if (request.url != pageUrl) {
        logger.warn("""
          Canceling request for an external page with url: ${request.url}.
          Current page url: ${browser.url}
          Target safe url: ${pageUrl}
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
      "PreviewClickGuard.js",
      "ScrollSync.js"
    )

    private val baseStyles = emptyList<String>()

    private fun isOffScreenRendering(): Boolean = Registry.`is`("ide.browser.jcef.markdownView.osr.enabled")

    private object ProhibitingResourceRequestHandler : CefResourceRequestHandlerAdapter() {
      override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): Boolean = true
    }
  }
}
