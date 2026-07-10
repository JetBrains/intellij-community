// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.jcef

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.ComponentBackedWebViewEngine
import com.intellij.ui.webview.impl.WebViewAssetResolver
import com.intellij.ui.webview.impl.WebViewAssetResponse
import com.intellij.ui.webview.impl.WebViewJsMessageReceiver
import com.intellij.ui.webview.impl.engine.WebViewScript
import com.intellij.ui.webview.impl.resolveWebViewAssetUrl
import com.intellij.ui.webview.impl.webViewAssetHttpsUrl
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.callback.CefQueryCallback
import org.cef.callback.CefRunContextMenuCallback
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.intellij.lang.annotations.Language
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<JcefWebViewEngine>()

internal class JcefWebViewEngine(
  parentScope: CoroutineScope,
  jbCefApp: JBCefApp,
  private val documentStartScripts: List<WebViewScript> = emptyList(),
) : ComponentBackedWebViewEngine {
  private companion object {
    private const val INITIAL_URL = "about:blank"
    private const val QUERY_FUNCTION = "__wviJcefQuery"
    private const val QUERY_CANCEL_FUNCTION = "__wviJcefQueryCancel"
    private const val EVAL_PREFIX = "__wvi_eval__"
    private const val EVAL_TIMEOUT_MS = 10_000L
    private const val DELIVERY_RETRY_DELAY_MS = 100L
    private const val MAX_PENDING_DELIVERIES = 128
  }

  @Suppress("RAW_SCOPE_CREATION") // Intentional: engine manages its own child scope lifecycle with close()
  private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
  private val closed = AtomicBoolean(false)
  private val browserReadyForNavigation = AtomicBoolean(false)
  private val nextEvalId = AtomicLong(0)
  private val pendingNavigationUrl = AtomicReference<String?>(null)
  private val activeAssetResolver = AtomicReference<WebViewAssetResolver?>(null)
  private val pendingEvals = ConcurrentHashMap<Long, CompletableDeferred<String?>>()
  private val inMemoryPages = ConcurrentHashMap<String, ByteArray>()
  private val pendingDeliveries = ArrayDeque<String>()
  private val deliveryLock = Any()
  private val pageLoaded = AtomicBoolean(false)
  private val bridgeReadyForDelivery = AtomicBoolean(false)
  private val deliveryFlushScheduled = AtomicBoolean(false)
  private val deliveryFlushRequested = AtomicBoolean(false)
  private val deliveryRetryScheduled = AtomicBoolean(false)

  @Volatile
  private var messageReceiver: WebViewJsMessageReceiver? = null

  private val jbCefClient: JBCefClient = jbCefApp.createClient()
  private val messageRouter: CefMessageRouter
  private val messageRouterHandler: CefMessageRouterHandlerAdapter
  private val jbCefBrowser: JBCefBrowser = JBCefBrowser.createBuilder()
    .setClient(jbCefClient)
    .setUrl(INITIAL_URL)
    .setEnableOpenDevToolsMenuItem(false)
    .build()

  override val component: JComponent

  private val cefBrowser: CefBrowser
    get() = jbCefBrowser.cefBrowser

  init {
    jbCefClient.addRequestHandler(createRequestHandler(), cefBrowser)
    jbCefClient.addLoadHandler(createLoadHandler(), cefBrowser)
    jbCefClient.addLifeSpanHandler(createLifeSpanHandler(), cefBrowser)
    jbCefClient.addContextMenuHandler(createContextMenuHandler(), cefBrowser)

    val routerConfig = CefMessageRouter.CefMessageRouterConfig(QUERY_FUNCTION, QUERY_CANCEL_FUNCTION)
    messageRouterHandler = createMessageRouterHandler()
    messageRouter = jbCefApp.createMessageRouter(routerConfig).also {
      it.addHandler(messageRouterHandler, true)
      jbCefClient.cefClient.addMessageRouter(it)
    }

    LOG.trace { "Created JCEF WebView engine; offScreenRendering=${jbCefBrowser.isOffScreenRendering}" }
    component = jbCefBrowser.component
  }

  override suspend fun loadFile(file: Path) {
    clearActiveAssetResolver()
    navigate(file.toUri().toString())
  }

  override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    activeAssetResolver.set(WebViewAssetResolver(root))
    navigate(webViewAssetHttpsUrl(entry, query))
  }

  override suspend fun loadHtml(html: String, baseFile: Path?) {
    clearActiveAssetResolver()
    val documentUrl = baseFile?.toUri()?.toString() ?: "https://ui.webview.local/in-memory/${System.nanoTime()}.html"
    inMemoryPages[documentUrl] = html.toByteArray(StandardCharsets.UTF_8)
    navigate(documentUrl)
  }

  private fun navigate(url: String) {
    resetPageStateForNavigation()
    pendingNavigationUrl.set(url)
    runOnEdt {
      flushPendingNavigation()
    }
  }

  private fun flushPendingNavigation() {
    if (closed.get() || !browserReadyForNavigation.get()) return
    val url = pendingNavigationUrl.getAndSet(null) ?: return
    cefBrowser.loadURL(url)
  }

  override suspend fun evaluateJavaScript(script: String): String? {
    if (closed.get()) return null
    if (!pageLoaded.get()) return null

    val evalId = nextEvalId.incrementAndGet()
    val result = CompletableDeferred<String?>()
    pendingEvals[evalId] = result
    runOnEdt {
      if (closed.get()) {
        pendingEvals.remove(evalId)?.complete(null)
        return@runOnEdt
      }
      cefBrowser.executeJavaScript(buildEvalScript(evalId, script), cefBrowser.url ?: INITIAL_URL, 0)
    }

    return try {
      withTimeoutOrNull(EVAL_TIMEOUT_MS.milliseconds) { result.await() }
    }
    finally {
      pendingEvals.remove(evalId)
    }
  }

  override suspend fun transferToJs(rawJson: String) {
    if (closed.get()) return
    enqueuePendingDelivery(rawJson)
    scheduleDeliveryFlush()
  }

  override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    messageReceiver = receiver
  }

  override suspend fun close() {
    if (!closed.compareAndSet(false, true)) return

    scope.cancel(CancellationException("JCEF WebView engine closed"))
    pendingEvals.values.forEach { it.complete(null) }
    pendingEvals.clear()
    clearActiveAssetResolver()
    inMemoryPages.clear()
    clearPendingDeliveries()
    pageLoaded.set(false)
    bridgeReadyForDelivery.set(false)
    deliveryFlushRequested.set(false)
    deliveryRetryScheduled.set(false)

    runOnEdtAndWait {
      runCatching { messageRouter.removeHandler(messageRouterHandler) }
        .onFailure { LOG.warn("Failed to remove JCEF message router handler", it) }
      runCatching { jbCefClient.cefClient.removeMessageRouter(messageRouter) }
        .onFailure { LOG.warn("Failed to remove JCEF message router", it) }
      runCatching { messageRouter.dispose() }
        .onFailure { LOG.warn("Failed to dispose JCEF message router", it) }
      runCatching { Disposer.dispose(jbCefBrowser) }
        .onFailure { LOG.warn("Failed to dispose JCEF browser", it) }
      runCatching { Disposer.dispose(jbCefClient) }
        .onFailure { LOG.warn("Failed to dispose JCEF client", it) }
    }
  }

  override fun requestWebViewFocus() {
    runOnEdt {
      cefBrowser.uiComponent.requestFocusInWindow()
      cefBrowser.setFocus(true)
    }
  }

  override fun clearWebViewFocus() {
    runOnEdt {
      cefBrowser.setFocus(false)
    }
  }

  private fun createMessageRouterHandler(): CefMessageRouterHandlerAdapter {
    return object : CefMessageRouterHandlerAdapter() {
      override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?,
      ): Boolean {
        val raw = request ?: return false
        if (handleEvalResult(raw)) {
          callback?.success("")
          return true
        }

        handleIncomingMessage(raw)
        callback?.success("")
        return true
      }
    }
  }

  private fun handleIncomingMessage(raw: String) {
    messageReceiver?.transferFromJs(raw)
  }

  private fun handleEvalResult(raw: String): Boolean {
    if (!raw.startsWith(EVAL_PREFIX)) return false

    val payload = raw.removePrefix(EVAL_PREFIX).removePrefix(":")
    val evalIdEnd = payload.indexOf(':')
    if (evalIdEnd < 0) return true
    val statusEnd = payload.indexOf(':', evalIdEnd + 1)
    if (statusEnd < 0) return true

    val evalId = payload.substring(0, evalIdEnd).toLongOrNull() ?: return true
    val status = payload.substring(evalIdEnd + 1, statusEnd)
    val value = payload.substring(statusEnd + 1)
    pendingEvals.remove(evalId)?.complete(value.takeIf { status == "ok" })
    return true
  }

  @Language("JavaScript")
  private fun buildEvalScript(evalId: Long, script: String): String {
    return """
      (function() {
        const finish = function(status, value) {
          let encoded = "";
          if (typeof value !== "undefined") {
            encoded = typeof value === "string" ? value : JSON.stringify(value);
          }
          window.$QUERY_FUNCTION({ request: "$EVAL_PREFIX:$evalId:" + status + ":" + encoded });
        };
        Promise.resolve()
          .then(function() { return eval(${quoteJsString(script)}); })
          .then(function(value) { finish("ok", value); })
          .catch(function(error) { finish("err", error && error.message ? error.message : String(error)); });
      })();
    """.trimIndent()
  }

  private fun createRequestHandler(): CefRequestHandlerAdapter {
    return object : CefRequestHandlerAdapter() {
      override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
      ): CefResourceRequestHandler? {
        val assetResponse = resolveAssetUrl(request.url)
        if (assetResponse != null) {
          return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler {
              return JcefBytesResourceHandler(assetResponse)
            }
          }
        }

        val htmlBytes = inMemoryPages[request.url] ?: return null
        return object : CefResourceRequestHandlerAdapter() {
          override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler {
            return JcefBytesResourceHandler(
              WebViewAssetResponse(
                statusCode = 200,
                statusText = "OK",
                contentType = "text/html; charset=utf-8",
                bytes = htmlBytes,
              )
            )
          }
        }
      }
    }
  }

  private fun createLoadHandler(): CefLoadHandlerAdapter {
    return object : CefLoadHandlerAdapter() {
      override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
        injectDocumentStartScripts(browser, frame)
        resetDeliveryReadiness(browser, frame)
      }

      override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        markReadyForNavigation(browser, frame)
        markPageLoaded(browser, frame)
      }

      override fun onLoadError(
        browser: CefBrowser?,
        frame: CefFrame?,
        errorCode: CefLoadHandler.ErrorCode?,
        errorText: String?,
        failedUrl: String?,
      ) {
        markReadyForNavigation(browser, frame)
        markPageLoaded(browser, frame)
        if (frame?.isMain == true && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) {
          LOG.warn("JCEF WebView load failed: url=$failedUrl, error=$errorCode, text=$errorText")
        }
      }
    }
  }

  private fun createLifeSpanHandler(): CefLifeSpanHandlerAdapter {
    return object : CefLifeSpanHandlerAdapter() {
      override fun onBeforeClose(browser: CefBrowser?) {
        pendingEvals.values.forEach { it.complete(null) }
        pendingEvals.clear()
        clearPendingDeliveries()
        pageLoaded.set(false)
        bridgeReadyForDelivery.set(false)
        deliveryFlushRequested.set(false)
        deliveryRetryScheduled.set(false)
      }
    }
  }

  private fun markReadyForNavigation(browser: CefBrowser?, frame: CefFrame?) {
    if (browser === cefBrowser && frame?.isMain == true && browserReadyForNavigation.compareAndSet(false, true)) {
      runOnEdt {
        flushPendingNavigation()
      }
    }
  }

  private fun injectDocumentStartScripts(browser: CefBrowser?, frame: CefFrame?) {
    if (browser !== cefBrowser || frame == null || documentStartScripts.isEmpty()) return
    for (script in documentStartScripts) {
      frame.executeJavaScript(script.script, frame.url ?: INITIAL_URL, 0)
    }
  }

  private fun resetDeliveryReadiness(browser: CefBrowser?, frame: CefFrame?) {
    if (browser !== cefBrowser || frame?.isMain != true) return
    resetPageStateForNavigation()
  }

  private fun resetPageStateForNavigation() {
    pageLoaded.set(false)
    bridgeReadyForDelivery.set(false)
    deliveryRetryScheduled.set(false)
  }

  private fun markPageLoaded(browser: CefBrowser?, frame: CefFrame?) {
    if (browser !== cefBrowser || frame?.isMain != true) return
    pageLoaded.set(true)
    scheduleDeliveryFlush()
  }

  private fun enqueuePendingDelivery(rawJson: String) {
    synchronized(deliveryLock) {
      if (pendingDeliveries.size >= MAX_PENDING_DELIVERIES) {
        pendingDeliveries.removeFirst()
        LOG.warn("Dropping queued JCEF WebView message because the JS bridge is not ready; queueLimit=$MAX_PENDING_DELIVERIES")
      }
      pendingDeliveries.addLast(rawJson)
    }
  }

  private fun clearPendingDeliveries() {
    synchronized(deliveryLock) {
      pendingDeliveries.clear()
    }
  }

  private fun clearActiveAssetResolver() {
    activeAssetResolver.set(null)
  }

  private fun resolveAssetUrl(url: String): WebViewAssetResponse? {
    return resolveWebViewAssetUrl(url, activeAssetResolver.get(), "jcef")
  }

  private fun hasPendingDeliveries(): Boolean = synchronized(deliveryLock) { pendingDeliveries.isNotEmpty() }

  private fun pollPendingDelivery(): String? = synchronized(deliveryLock) {
    if (pendingDeliveries.isEmpty()) null else pendingDeliveries.removeFirst()
  }

  private fun requeuePendingDelivery(rawJson: String) {
    synchronized(deliveryLock) {
      pendingDeliveries.addFirst(rawJson)
    }
  }

  private fun scheduleDeliveryFlush() {
    if (closed.get() || !pageLoaded.get()) return
    if (!deliveryFlushScheduled.compareAndSet(false, true)) {
      deliveryFlushRequested.set(true)
      return
    }

    scope.launch {
      try {
        flushPendingDeliveries()
      }
      finally {
        deliveryFlushScheduled.set(false)
        val requested = deliveryFlushRequested.getAndSet(false)
        if (!closed.get() && pageLoaded.get() && hasPendingDeliveries()) {
          if (requested || bridgeReadyForDelivery.get()) {
            scheduleDeliveryFlush()
          }
          else {
            scheduleDeliveryRetry()
          }
        }
      }
    }
  }

  private fun scheduleDeliveryRetry() {
    if (!deliveryRetryScheduled.compareAndSet(false, true)) return

    scope.launch {
      delay(DELIVERY_RETRY_DELAY_MS.milliseconds)
      deliveryRetryScheduled.set(false)
      if (!closed.get() && pageLoaded.get() && hasPendingDeliveries()) {
        scheduleDeliveryFlush()
      }
    }
  }

  private suspend fun flushPendingDeliveries() {
    if (closed.get() || !pageLoaded.get() || !hasPendingDeliveries()) return
    if (!bridgeReadyForDelivery.get()) {
      if (!isJavaScriptBridgeReady()) return
      bridgeReadyForDelivery.set(true)
    }

    while (!closed.get() && pageLoaded.get()) {
      val rawJson = pollPendingDelivery() ?: return
      val delivered = evaluateJavaScript(buildDeliveryScript(rawJson)) == "true"
      if (!delivered) {
        bridgeReadyForDelivery.set(false)
        requeuePendingDelivery(rawJson)
        return
      }
    }
  }

  private suspend fun isJavaScriptBridgeReady(): Boolean {
    return evaluateJavaScript(
      /*language=JavaScript*/ "Boolean(document.readyState !== 'loading' && window.__WVI__ && typeof window.__WVI__[\"__deliver\"] === 'function')",
    ) == "true"
  }

  @Language("JavaScript")
  private fun buildDeliveryScript(rawJson: String): String {
    return """
      (function() {
        if (!window.__WVI__ || typeof window.__WVI__["__deliver"] !== "function") {
          return false;
        }
        window.__WVI__["__deliver"](${quoteJsString(rawJson)});
        return true;
      })()
    """.trimIndent()
  }

  private fun createContextMenuHandler(): CefContextMenuHandlerAdapter {
    return object : CefContextMenuHandlerAdapter() {
      override fun onBeforeContextMenu(browser: CefBrowser?, frame: CefFrame?, params: CefContextMenuParams?, model: CefMenuModel?) {
        model?.clear()
      }

      override fun runContextMenu(
        browser: CefBrowser?,
        frame: CefFrame?,
        params: CefContextMenuParams?,
        model: CefMenuModel?,
        callback: CefRunContextMenuCallback?,
      ): Boolean {
        callback?.cancel()
        return true
      }
    }
  }

  private fun quoteJsString(value: String): String = JsonPrimitive(value).toString()

  private fun runOnEdt(action: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) {
      action()
    }
    else {
      SwingUtilities.invokeLater(action)
    }
  }

  private suspend fun runOnEdtAndWait(action: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) {
      action()
      return
    }

    val result = CompletableDeferred<Unit>()
    SwingUtilities.invokeLater {
      runCatching(action)
        .onSuccess { result.complete(Unit) }
        .onFailure { result.completeExceptionally(it) }
    }
    result.await()
  }

}

internal fun createJcefWebViewEngine(
  parentScope: CoroutineScope,
  documentStartScripts: List<WebViewScript> = emptyList(),
): JcefWebViewEngine {
  return JcefWebViewEngine(parentScope, JcefWebViewRuntime.getOrCreateJBCefApp(), documentStartScripts)
}
