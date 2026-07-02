// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.openapi.application.PathManager
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.WebViewAssetResolver
import com.intellij.ui.webview.impl.WebViewAssetResponse
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.ui.webview.impl.resolveWebViewAssetUrl
import com.intellij.ui.webview.impl.webViewAssetHttpsUrl
import com.intellij.ui.webview.impl.WebViewJsMessageReceiver
import com.intellij.ui.webview.impl.engine.WebViewScript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

@ApiStatus.Internal
internal class WinWebViewEngine(
  parentScope: CoroutineScope,
  private val bridge: WinWebView2BridgeApi = NativeWinWebView2BridgeApi,
  private val debugName: String? = null,
  documentStartScripts: List<WebViewScript> = emptyList(),
  private val webViewDispatcher: CoroutineDispatcher = WebView2Dispatcher.coroutineDispatcher,
) : WebViewEngineBridge {
  override val isHeavyweight: Boolean = true

  private enum class State { New, Creating, Active, Closing, Closed }

  private enum class FocusOp { Focus, Clear }

  private sealed interface PendingLoad {
    data class Url(val url: String) : PendingLoad
    data class Html(val html: String, val baseUrl: String?) : PendingLoad
  }

  private data class PendingBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val scale: Double,
  )

  private val state = AtomicReference(State.New)

  @Suppress("RAW_SCOPE_CREATION") // Intentional: engine manages its own child scope lifecycle with close()
  private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

  private val handleReady = AtomicReference(CompletableDeferred<Long>())
  private val nextEvalId = AtomicLong(0)
  private val pendingEvals = ConcurrentHashMap<Long, (String?) -> Unit>()
  private val activeAssetResolver = AtomicReference<WebViewAssetResolver?>(null)
  private val recoveryAttempts = ArrayDeque<Long>()
  private val documentStartScript = documentStartScripts.joinToString("\n;\n") { it.script }

  @Volatile
  private var nativeHandle: Long = 0

  @Volatile
  private var inboundMessageHandler: (String) -> Unit = {}

  private val pendingLoad = AtomicReference<PendingLoad?>(null)
  private val loadScheduled = AtomicBoolean(false)

  @Volatile
  private var lastLoad: PendingLoad? = null

  private val pendingBounds = AtomicReference<PendingBounds?>(null)
  private val boundsScheduled = AtomicBoolean(false)

  @Volatile
  private var lastBounds: PendingBounds? = null

  private val visibilityScheduled = AtomicBoolean(false)
  private val pendingFocusOp = AtomicReference<FocusOp?>(null)
  private val focusScheduled = AtomicBoolean(false)
  private val pendingAttachParent = AtomicLong(0)
  private val attachScheduled = AtomicBoolean(false)

  @Volatile
  private var currentParentHwnd: Long = 0

  @Volatile
  private var shortcutTarget: Component? = null

  @Volatile
  private var focusGainedHandler: () -> Unit = {}

  @Volatile
  private var beforeMouseFocusHandler: () -> Unit = {}

  @Volatile
  private var hidden = false

  private var consecutiveRenderUnresponsiveCount = 0

  private val callbacks = object : WinWebView2Bridge.Callbacks {
    override fun onCreated(handle: Long) {
      if (state.get() != State.Creating || (nativeHandle != 0L && nativeHandle != handle)) {
        bridge.destroy(handle)
        return
      }

      nativeHandle = handle
      state.set(State.Active)
      consecutiveRenderUnresponsiveCount = 0
      handleReady.get().complete(handle)
      applyPendingState(handle)
      WebViewLogger.logLifecycle("win-webview2-create", "WebView2 ready${diagnosticContext()}")
    }

    override fun onCreateFailed(message: String) {
      state.set(State.Closed)
      handleReady.get().completeExceptionally(IllegalStateException(message))
      cancelPendingEvaluations()
      clearActiveAssetResolver()
      WebViewLogger.LOG.error("Failed to initialize WebView2${messageWithContext(message)}")
    }

    override fun onMessage(raw: String) {
      inboundMessageHandler(raw)
    }

    override fun onEvaluationResult(evalId: Long, result: String?) {
      pendingEvals.remove(evalId)?.invoke(result)
    }

    override fun onEvaluationError(evalId: Long, message: String) {
      WebViewLogger.LOG.warn("WebView2 JavaScript evaluation failed: $message")
      pendingEvals.remove(evalId)?.invoke(null)
    }

    override fun onAcceleratorKeyPressed(keyEventKind: Int, virtualKey: Int, modifiers: Int, keyEventLParam: Int): Boolean {
      return WinWebViewShortcutInterop.handleAcceleratorKeyPressed(shortcutTarget, keyEventKind, virtualKey, modifiers, keyEventLParam)
    }

    override fun onBeforeMouseFocus() {
      beforeMouseFocusHandler()
    }

    override fun onFocusGained() {
      focusGainedHandler()
    }

    override fun onLog(level: Int, message: String) {
      when {
        level >= NATIVE_DIAGNOSTIC_ERROR -> WebViewLogger.LOG.error(message)
        level >= NATIVE_DIAGNOSTIC_WARN -> WebViewLogger.LOG.warn(message)
        level >= NATIVE_DIAGNOSTIC_INFO -> WebViewLogger.LOG.info(message)
        level >= NATIVE_DIAGNOSTIC_DEBUG -> WebViewLogger.LOG.debug(message)
        else -> WebViewLogger.LOG.trace(message)
      }
    }

    override fun onNativeDiagnostic(level: Int, event: String, message: String, data: String) {
      logNativeDiagnostic(level, event, message, data)
      when (event) {
        NATIVE_EVENT_PROCESS_FAILED_FATAL, NATIVE_EVENT_BROWSER_PROCESS_EXITED_FATAL -> invokeOnWebView {
          recoverAfterFatalNativeFailure(event, message, data)
        }
        NATIVE_EVENT_PROCESS_FAILED_UNRESPONSIVE -> invokeOnWebView {
          handleRenderProcessUnresponsive(message, data)
        }
      }
    }

    override fun resolveAsset(url: String): WinWebView2Bridge.AssetResponse? {
      val response = resolveWebViewAssetUrl(url, activeAssetResolver.get()) ?: return null
      return response.toNativeAssetResponse()
    }
  }

  override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    inboundMessageHandler = receiver::transferFromJs
  }

  internal fun attachToParent(parentHwnd: Long) {
    currentParentHwnd = parentHwnd
    pendingAttachParent.set(parentHwnd)
    if (state.compareAndSet(State.New, State.Creating)) {
      invokeOnWebView { performCreate() }
      return
    }
    val currentState = state.get()
    if (currentState == State.Creating || currentState == State.Active) {
      scheduleAttachApply()
    }
  }

  internal fun attachToParent(parentHwnd: Long, x: Int, y: Int, width: Int, height: Int, scale: Double) {
    pendingBounds.set(PendingBounds(x, y, width, height, scale))
    attachToParent(parentHwnd)
  }

  internal fun detachFromParent() {
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    invokeOnWebView { bridge.detachFromParent(handle) }
  }

  internal fun setBounds(x: Int, y: Int, width: Int, height: Int, scale: Double) {
    pendingBounds.set(PendingBounds(x, y, width, height, scale))
    if (state.get() == State.Closed) return
    scheduleBoundsApply()
  }

  internal fun setHidden(hidden: Boolean) {
    this.hidden = hidden
    if (state.get() == State.Closed) return
    scheduleVisibilityApply()
  }

  internal fun requestFocus() {
    if (state.get() != State.Active) return
    pendingFocusOp.set(FocusOp.Focus)
    scheduleFocusApply()
  }

  internal fun clearFocus() {
    if (state.get() != State.Active) return
    pendingFocusOp.set(FocusOp.Clear)
    scheduleFocusApply()
  }

  internal fun setShortcutTarget(target: Component?) {
    shortcutTarget = target
  }

  internal fun setFocusGainedHandler(handler: (() -> Unit)?) {
    focusGainedHandler = handler ?: {}
  }

  internal fun setBeforeMouseFocusHandler(handler: (() -> Unit)?) {
    beforeMouseFocusHandler = handler ?: {}
  }

  override suspend fun loadFile(file: Path) {
    clearActiveAssetResolver()
    loadUrlInternal(file.toUri().toString())
  }

  override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    activeAssetResolver.set(WebViewAssetResolver(root))
    loadUrlInternal(webViewAssetHttpsUrl(entry, query))
  }

  override suspend fun loadHtml(html: String, baseFile: Path?) {
    clearActiveAssetResolver()
    loadHtmlInternal(html, baseFile?.toUri()?.toString())
  }

  private fun loadUrlInternal(url: String) {
    val load = PendingLoad.Url(url)
    pendingLoad.set(load)
    lastLoad = load
    if (state.get() == State.Closed) return
    scheduleLoadApply()
  }

  private fun loadHtmlInternal(html: String, baseUrl: String?) {
    val load = PendingLoad.Html(html, baseUrl)
    pendingLoad.set(load)
    lastLoad = load
    if (state.get() == State.Closed) return
    scheduleLoadApply()
  }

  override suspend fun evaluateJavaScript(script: String): String? {
    if (state.get() == State.New || state.get() == State.Closing || state.get() == State.Closed) return null
    val handle = awaitHandle() ?: return null
    if (state.get() != State.Active) return null

    val evalId = nextEvalId.incrementAndGet()
    return suspendCancellableCoroutine { continuation ->
      pendingEvals[evalId] = { result ->
        if (continuation.isActive) {
          continuation.resume(result)
        }
      }

      continuation.invokeOnCancellation {
        pendingEvals.remove(evalId)
      }

      invokeOnWebView {
        if (state.get() != State.Active) {
          pendingEvals.remove(evalId)?.invoke(null)
          return@invokeOnWebView
        }
        bridge.evaluateJavaScript(handle, evalId, script)
      }
    }
  }

  override suspend fun transferToJs(rawJson: String) {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    invokeOnWebView {
      if (state.get() != State.Active) return@invokeOnWebView
      try {
        bridge.transferToJs(handle, rawJson)
      }
      catch (t: IllegalStateException) {
        WebViewLogger.LOG.debug("Dropping WebView2 message while the view is not ready", t)
      }
    }
  }

  override suspend fun close() {
    loop@ while (true) {
      when (val current = state.get()) {
        State.New -> {
          if (state.compareAndSet(State.New, State.Closed)) {
            scope.cancel()
            cancelPendingEvaluations()
            clearActiveAssetResolver()
            handleReady.get().cancel(CancellationException("Engine closed before initialization"))
            WebViewLogger.logLifecycle("win-webview2-close", "closed from New state")
            return
          }
        }
        State.Creating, State.Active -> {
          if (state.compareAndSet(current, State.Closing)) break@loop
        }
        State.Closing, State.Closed -> {
          WebViewLogger.logLifecycle("win-webview2-close", "already closing/closed, idempotent no-op")
          return
        }
      }
    }

    cancelPendingEvaluations()
    clearActiveAssetResolver()

    val handle = nativeHandle
    nativeHandle = 0
    if (handle != 0L) {
      invokeOnWebView {
        bridge.destroy(handle)
        handleReady.get().cancel(CancellationException("Engine closed"))
        state.set(State.Closed)
        WebViewLogger.logLifecycle("win-webview2-close", "native cleanup complete")
      }
    }
    else {
      handleReady.get().cancel(CancellationException("Engine closed"))
      state.set(State.Closed)
    }
    scope.cancel()
  }

  private fun applyPendingState(handle: Long) {
    applyAttachmentState(handle)
    when (val load = pendingLoad.getAndSet(null)) {
      is PendingLoad.Url -> bridge.loadUrl(handle, load.url)
      is PendingLoad.Html -> bridge.loadHtml(handle, load.html, load.baseUrl)
      null -> Unit
    }
  }

  private fun applyAttachmentState(handle: Long) {
    pendingBounds.getAndSet(null)?.let {
      lastBounds = it
      applyBounds(handle, it)
    }
    applyVisibility(handle)
  }

  private fun performCreate() {
    val parent = pendingAttachParent.getAndSet(0)
    if (parent == 0L || state.get() == State.Closed) return
    try {
      WebViewLogger.logLifecycle("win-webview2-create", "initializing WebView2${diagnosticContext()}")
      nativeHandle = bridge.create(parent, userDataDir().toString(), documentStartScript, callbacks)
      applyAttachmentState(nativeHandle)
    }
    catch (t: Throwable) {
      state.set(State.Closed)
      handleReady.get().completeExceptionally(t)
      cancelPendingEvaluations()
      clearActiveAssetResolver()
      WebViewLogger.LOG.error("Failed to start WebView2 initialization${diagnosticContext()}", t)
    }
  }

  private fun scheduleAttachApply() {
    if (!attachScheduled.compareAndSet(false, true)) return
    invokeOnWebView {
      attachScheduled.set(false)
      val handle = nativeHandle
      if (handle == 0L || state.get() == State.Closed) return@invokeOnWebView
      val parent = pendingAttachParent.getAndSet(0)
      if (parent == 0L) return@invokeOnWebView
      bridge.attachToParent(handle, parent)
      applyAttachmentState(handle)
    }
  }

  private fun applyBounds(handle: Long, bounds: PendingBounds) {
    bridge.setBounds(handle, bounds.x, bounds.y, bounds.width, bounds.height, bounds.scale)
  }

  private fun scheduleBoundsApply() {
    if (!boundsScheduled.compareAndSet(false, true)) return
    invokeOnWebView {
      boundsScheduled.set(false)
      val handle = nativeHandle
      if (handle == 0L || state.get() == State.Closed) return@invokeOnWebView
      val bounds = pendingBounds.getAndSet(null) ?: return@invokeOnWebView
      lastBounds = bounds
      applyBounds(handle, bounds)
    }
  }

  private fun scheduleVisibilityApply() {
    if (!visibilityScheduled.compareAndSet(false, true)) return
    invokeOnWebView {
      visibilityScheduled.set(false)
      val handle = nativeHandle
      if (handle == 0L || state.get() == State.Closed) return@invokeOnWebView
      applyVisibility(handle)
    }
  }

  private fun applyVisibility(handle: Long) {
    bridge.setVisible(handle, !hidden && state.get() == State.Active)
  }

  private fun scheduleFocusApply() {
    if (!focusScheduled.compareAndSet(false, true)) return
    invokeOnWebView {
      focusScheduled.set(false)
      val handle = nativeHandle
      if (handle == 0L || state.get() != State.Active) {
        pendingFocusOp.set(null)
        return@invokeOnWebView
      }
      val focusOp = pendingFocusOp.getAndSet(null)
      try {
        when (focusOp) {
          FocusOp.Focus -> bridge.focus(handle)
          FocusOp.Clear -> bridge.clearFocus(handle)
          null -> Unit
        }
      }
      catch (e: IllegalStateException) {
        WebViewLogger.LOG.debug("Failed to apply WebView2 focus operation: operation=$focusOp${diagnosticContext()}", e)
      }
    }
  }

  private fun scheduleLoadApply() {
    if (!loadScheduled.compareAndSet(false, true)) return
    invokeOnWebView {
      loadScheduled.set(false)
      val handle = nativeHandle
      if (handle == 0L || state.get() != State.Active) return@invokeOnWebView
      when (val load = pendingLoad.getAndSet(null)) {
        is PendingLoad.Url -> bridge.loadUrl(handle, load.url)
        is PendingLoad.Html -> bridge.loadHtml(handle, load.html, load.baseUrl)
        null -> Unit
      }
    }
  }

  private suspend fun awaitHandle(): Long? {
    val handle = nativeHandle
    if (handle != 0L && state.get() == State.Active) return handle

    return try {
      handleReady.get().await()
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun logNativeDiagnostic(level: Int, event: String, message: String, data: String) {
    val formattedMessage = buildString {
      append("WebView2 native diagnostic")
      append(diagnosticContext())
      append(": ")
      append(event)
      if (message.isNotBlank()) {
        append(" - ")
        append(message)
      }
      if (data.isNotBlank()) {
        append(" [")
        append(data.replace('\n', ';'))
        append(']')
      }
    }
    when {
      level >= NATIVE_DIAGNOSTIC_ERROR -> WebViewLogger.LOG.error(formattedMessage)
      level >= NATIVE_DIAGNOSTIC_WARN -> WebViewLogger.LOG.warn(formattedMessage)
      level >= NATIVE_DIAGNOSTIC_INFO -> WebViewLogger.LOG.info(formattedMessage)
      level >= NATIVE_DIAGNOSTIC_DEBUG -> WebViewLogger.LOG.debug(formattedMessage)
      else -> WebViewLogger.LOG.trace(formattedMessage)
    }
  }

  private fun handleRenderProcessUnresponsive(message: String, data: String) {
    consecutiveRenderUnresponsiveCount++
    if (consecutiveRenderUnresponsiveCount >= MAX_RENDER_UNRESPONSIVE_BEFORE_RECOVERY) {
      recoverAfterFatalNativeFailure(NATIVE_EVENT_PROCESS_FAILED_UNRESPONSIVE, message, data)
    }
  }

  private fun recoverAfterFatalNativeFailure(event: String, message: String, data: String) {
    val current = state.get()
    if (current == State.Closing || current == State.Closed) return

    val parentHwnd = currentParentHwnd
    if (parentHwnd == 0L) {
      closeAfterFatalNativeFailure(event, message, data, IllegalStateException("Cannot recover WebView2 without a parent HWND"))
      return
    }
    if (!recordRecoveryAttempt()) {
      closeAfterFatalNativeFailure(event, message, data, IllegalStateException("WebView2 recovery limit exceeded"))
      return
    }

    val oldHandle = nativeHandle
    nativeHandle = 0
    state.set(State.Creating)
    cancelPendingEvaluations()
    resetHandleReady("WebView2 is recovering after $event")
    pendingLoad.set(lastLoad)
    lastBounds?.let { pendingBounds.set(it) }
    consecutiveRenderUnresponsiveCount = 0

    if (oldHandle != 0L) {
      runCatching { bridge.destroy(oldHandle) }
        .onFailure { WebViewLogger.LOG.error("Failed to destroy crashed WebView2 handle${diagnosticContext()}", it) }
    }

    try {
      WebViewLogger.logLifecycle("win-webview2-recovery", "recreating after $event${diagnosticContext()}")
      nativeHandle = bridge.create(parentHwnd, userDataDir().toString(), documentStartScript, callbacks)
      bridge.setVisible(nativeHandle, !hidden)
    }
    catch (t: Throwable) {
      closeAfterFatalNativeFailure(event, message, data, t)
    }
  }

  private fun closeAfterFatalNativeFailure(event: String, message: String, data: String, cause: Throwable) {
    val oldHandle = nativeHandle
    nativeHandle = 0
    state.set(State.Closed)
    cancelPendingEvaluations()
    clearActiveAssetResolver()
    scope.cancel()
    handleReady.get().completeExceptionally(cause)
    if (oldHandle != 0L) {
      runCatching { bridge.destroy(oldHandle) }
        .onFailure { WebViewLogger.LOG.error("Failed to destroy WebView2 handle after fatal failure${diagnosticContext()}", it) }
    }
    WebViewLogger.LOG.error("WebView2 engine closed after fatal native failure${diagnosticContext()}: $event - $message [$data]", cause)
  }

  private fun recordRecoveryAttempt(): Boolean {
    val now = System.currentTimeMillis()
    while (recoveryAttempts.isNotEmpty() && now - recoveryAttempts.first() > RECOVERY_WINDOW_MILLIS) {
      recoveryAttempts.removeFirst()
    }
    if (recoveryAttempts.size >= MAX_RECOVERY_ATTEMPTS) return false
    recoveryAttempts.addLast(now)
    return true
  }

  private fun resetHandleReady(reason: String) {
    val next = CompletableDeferred<Long>()
    val previous = handleReady.getAndSet(next)
    if (!previous.isCompleted) {
      previous.cancel(CancellationException(reason))
    }
  }

  private fun diagnosticContext(): String {
    return debugName?.let { " [$it]" }.orEmpty()
  }

  private fun messageWithContext(message: String): String {
    val context = diagnosticContext()
    return when {
      message.isBlank() -> context
      context.isBlank() -> ": $message"
      else -> "$context: $message"
    }
  }

  private fun cancelPendingEvaluations() {
    pendingEvals.keys.forEach { evalId ->
      pendingEvals.remove(evalId)?.invoke(null)
    }
  }

  private fun clearActiveAssetResolver() {
    activeAssetResolver.set(null)
  }

  private fun WebViewAssetResponse.toNativeAssetResponse(): WinWebView2Bridge.AssetResponse {
    return WinWebView2Bridge.AssetResponse(
      statusCode = statusCode,
      statusText = statusText,
      headers = WinWebView2Bridge.AssetResponse.headers(contentType, headers),
      bytes = bytes,
    )
  }

  private fun userDataDir(): Path = Path.of(PathManager.getSystemPath(), "webview2")

  /**
   * Schedules [action] for execution on the dedicated WebView2 STA thread.
   *
   * Returns immediately; safe to call from EDT or any other thread.
   * Tasks are delivered in FIFO order via `PostThreadMessageW` and are not
   * tied to the engine's coroutine scope — they will run even if the scope
   * is cancelled, which is required to let `close()` actually destroy the
   * native handle.
   */
  private fun invokeOnWebView(action: () -> Unit) {
    webViewDispatcher.dispatch(EmptyCoroutineContext, Runnable { action() })
  }

  private companion object {
    private const val NATIVE_DIAGNOSTIC_DEBUG = 1
    private const val NATIVE_DIAGNOSTIC_INFO = 2
    private const val NATIVE_DIAGNOSTIC_WARN = 3
    private const val NATIVE_DIAGNOSTIC_ERROR = 4
    private const val MAX_RECOVERY_ATTEMPTS = 2
    private const val RECOVERY_WINDOW_MILLIS = 60_000L
    private const val MAX_RENDER_UNRESPONSIVE_BEFORE_RECOVERY = 2
    private const val NATIVE_EVENT_PROCESS_FAILED_FATAL = "process-failed.fatal"
    private const val NATIVE_EVENT_PROCESS_FAILED_UNRESPONSIVE = "process-failed.unresponsive"
    private const val NATIVE_EVENT_BROWSER_PROCESS_EXITED_FATAL = "browser-process-exited.fatal"
  }
}

@ApiStatus.Internal
internal fun createWinWebViewEngine(
  parentScope: CoroutineScope,
  debugName: String? = null,
  documentStartScripts: List<WebViewScript> = emptyList(),
): WinWebViewEngine {
  return WinWebViewEngine(parentScope, debugName = debugName, documentStartScripts = documentStartScripts)
}
