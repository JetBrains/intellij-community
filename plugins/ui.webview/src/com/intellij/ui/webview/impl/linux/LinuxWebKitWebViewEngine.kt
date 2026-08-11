// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.ui.webview.impl.WebViewJsMessageReceiver
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

@ApiStatus.Internal
internal class LinuxWebKitWebViewEngine(
  parentScope: CoroutineScope,
  internal val backend: LinuxWebKitBackend,
) : WebViewEngineBridge {
  override val isHeavyweight: Boolean = backend == LinuxWebKitBackend.X11

  private enum class State { New, Creating, Active, Closing, Closed }

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

  private val handleReady = CompletableDeferred<Long>()
  private val nextEvalId = AtomicLong(0)
  private val pendingEvals = ConcurrentHashMap<Long, (String?) -> Unit>()

  @Volatile
  private var nativeHandle: Long = 0

  @Volatile
  private var inboundMessageHandler: (String) -> Unit = {}

  @Volatile
  private var snapshotHandler: ((Int, Int, IntArray) -> Unit)? = null

  @Volatile
  private var pendingLoad: PendingLoad? = null

  @Volatile
  private var pendingBounds: PendingBounds? = null

  @Volatile
  private var hidden = false

  private val callbacks = object : LinuxWebKitGtkBridge.Callbacks {
    override fun onCreated(handle: Long) {
      runOnEdt {
        if (state.get() != State.Creating || (nativeHandle != 0L && nativeHandle != handle)) {
          return@runOnEdt
        }

        nativeHandle = handle
        state.set(State.Active)
        handleReady.complete(handle)
        applyPendingState(handle)
        WebViewLogger.logLifecycle("linux-webkitgtk-create", "WebKitGTK ready")
      }
    }

    override fun onCreateFailed(message: String) {
      state.set(State.Closed)
      handleReady.completeExceptionally(IllegalStateException(message))
      cancelPendingEvaluations()
      WebViewLogger.LOG.warn("Failed to initialize WebKitGTK: $message")
    }

    override fun onMessage(raw: String) {
      inboundMessageHandler(raw)
    }

    override fun onEvaluationResult(evalId: Long, result: String?) {
      pendingEvals.remove(evalId)?.invoke(result)
    }

    override fun onEvaluationError(evalId: Long, message: String) {
      WebViewLogger.LOG.warn("WebKitGTK JavaScript evaluation failed: $message")
      pendingEvals.remove(evalId)?.invoke(null)
    }

    override fun onSnapshot(width: Int, height: Int, pixels: IntArray) {
      val handler = snapshotHandler ?: return
      runOnEdt { handler(width, height, pixels) }
    }

    override fun onLog(level: Int, message: String) {
      when {
        level >= 2 -> WebViewLogger.LOG.warn(message)
        else -> WebViewLogger.LOG.info(message)
      }
    }
  }

  override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    inboundMessageHandler = receiver::transferFromJs
  }

  internal fun setSnapshotHandler(handler: ((Int, Int, IntArray) -> Unit)?) {
    snapshotHandler = handler
  }

  private fun createNativeAsync(parentWindowHandle: Long) {
    AppExecutorUtil.getAppExecutorService().execute {
      val handle = try {
        WebViewLogger.logLifecycle("linux-webkitgtk-create", "initializing WebKitGTK")
        LinuxWebKitGtkBridge.create(parentWindowHandle, backend, callbacks)
      }
      catch (t: Throwable) {
        if (state.compareAndSet(State.Creating, State.Closed)) {
          handleReady.completeExceptionally(t)
          cancelPendingEvaluations()
        }
        WebViewLogger.LOG.warn("Failed to start WebKitGTK initialization", t)
        return@execute
      }

      when {
        state.get() == State.Creating && (nativeHandle == 0L || nativeHandle == handle) -> {
          nativeHandle = handle
          LinuxWebKitGtkBridge.setVisible(handle, !hidden)
        }
        state.get() == State.Active && nativeHandle == handle -> {
          LinuxWebKitGtkBridge.setVisible(handle, !hidden)
        }
        else -> destroyStaleHandle(handle)
      }
    }
  }

  private fun destroyStaleHandle(handle: Long) {
    try {
      LinuxWebKitGtkBridge.destroy(handle)
    }
    catch (t: Throwable) {
      WebViewLogger.LOG.warn("Failed to destroy stale WebKitGTK handle", t)
    }
  }

  internal fun attachOffscreen() {
    attachToNativeParent(0)
  }

  internal fun attachToX11Parent(parentXid: Long) {
    attachToNativeParent(parentXid)
  }

  private fun attachToNativeParent(parentWindowHandle: Long) {
    while (true) {
      when (state.get()) {
        State.New -> {
          if (!state.compareAndSet(State.New, State.Creating)) continue
          createNativeAsync(parentWindowHandle)
          return
        }
        State.Creating -> {
          val handle = nativeHandle
          if (handle != 0L) {
            runOnEdt { LinuxWebKitGtkBridge.attachToParent(handle, parentWindowHandle) }
          }
          return
        }
        State.Active -> {
          val handle = nativeHandle
          if (handle != 0L) {
            runOnEdt {
              LinuxWebKitGtkBridge.attachToParent(handle, parentWindowHandle)
              applyAttachmentState(handle)
            }
          }
          return
        }
        State.Closing, State.Closed -> return
      }
    }
  }

  internal fun detach() {
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { LinuxWebKitGtkBridge.detach(handle) }
  }

  internal fun setBounds(x: Int, y: Int, width: Int, height: Int, scale: Double) {
    val bounds = PendingBounds(x, y, width, height, scale)
    pendingBounds = bounds
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { applyBounds(handle, bounds) }
  }

  internal fun setHidden(hidden: Boolean) {
    this.hidden = hidden
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { LinuxWebKitGtkBridge.setVisible(handle, !hidden) }
  }

  internal fun requestFocus() {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.focus(handle) }
  }

  internal fun clearFocus() {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.clearFocus(handle) }
  }

  override suspend fun loadFile(file: Path) {
    loadUrlInternal(file.toUri().toString())
  }

  override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    error("Linux WebKitGTK WebView asset handlers are not implemented; use JCEF on Linux")
  }

  override suspend fun loadHtml(html: String, baseFile: Path?) {
    loadHtmlInternal(html, baseFile?.toUri()?.toString())
  }

  private fun loadUrlInternal(url: String) {
    val load = PendingLoad.Url(url)
    pendingLoad = load
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt {
      LinuxWebKitGtkBridge.loadUrl(handle, url)
      markLoadApplied(load)
    }
  }

  private fun loadHtmlInternal(html: String, baseUrl: String?) {
    val load = PendingLoad.Html(html, baseUrl)
    pendingLoad = load
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt {
      LinuxWebKitGtkBridge.loadHtml(handle, html, baseUrl)
      markLoadApplied(load)
    }
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

      runOnEdt {
        if (state.get() != State.Active) {
          pendingEvals.remove(evalId)?.invoke(null)
          return@runOnEdt
        }
        LinuxWebKitGtkBridge.evaluateJavaScript(handle, evalId, script)
      }
    }
  }

  override suspend fun transferToJs(rawJson: String) {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt {
      LinuxWebKitGtkBridge.transferToJs(handle, rawJson)
    }
  }

  override suspend fun close() {
    loop@ while (true) {
      when (val current = state.get()) {
        State.New -> {
          if (state.compareAndSet(State.New, State.Closed)) {
            scope.cancel()
            cancelPendingEvaluations()
            handleReady.cancel(CancellationException("Engine closed before initialization"))
            WebViewLogger.logLifecycle("linux-webkitgtk-close", "closed from New state")
            return
          }
        }
        State.Creating, State.Active -> {
          if (state.compareAndSet(current, State.Closing)) break@loop
        }
        State.Closing, State.Closed -> {
          WebViewLogger.logLifecycle("linux-webkitgtk-close", "already closing/closed, idempotent no-op")
          return
        }
      }
    }

    cancelPendingEvaluations()
    scope.cancel()

    val handle = nativeHandle
    nativeHandle = 0
    if (handle != 0L) {
      runOnEdt {
        LinuxWebKitGtkBridge.destroy(handle)
        handleReady.cancel(CancellationException("Engine closed"))
        state.set(State.Closed)
        WebViewLogger.logLifecycle("linux-webkitgtk-close", "native cleanup complete")
      }
    }
    else {
      handleReady.cancel(CancellationException("Engine closed"))
      state.set(State.Closed)
    }
  }

  private fun applyPendingState(handle: Long) {
    applyAttachmentState(handle)
    when (val load = pendingLoad) {
      is PendingLoad.Url -> {
        LinuxWebKitGtkBridge.loadUrl(handle, load.url)
        markLoadApplied(load)
      }
      is PendingLoad.Html -> {
        LinuxWebKitGtkBridge.loadHtml(handle, load.html, load.baseUrl)
        markLoadApplied(load)
      }
      null -> Unit
    }
  }

  private fun applyAttachmentState(handle: Long) {
    pendingBounds?.let { applyBounds(handle, it) }
    LinuxWebKitGtkBridge.setVisible(handle, !hidden)
  }

  private fun markLoadApplied(load: PendingLoad?) {
    if (pendingLoad === load) {
      pendingLoad = null
    }
  }

  private fun applyBounds(handle: Long, bounds: PendingBounds) {
    LinuxWebKitGtkBridge.setBounds(handle, bounds.x, bounds.y, bounds.width, bounds.height, bounds.scale)
  }

  private suspend fun awaitHandle(): Long? {
    val handle = nativeHandle
    if (handle != 0L && state.get() == State.Active) return handle

    return try {
      handleReady.await()
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun cancelPendingEvaluations() {
    pendingEvals.keys.forEach { evalId ->
      pendingEvals.remove(evalId)?.invoke(null)
    }
  }

  private fun runOnEdt(action: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) {
      action()
    }
    else {
      SwingUtilities.invokeLater(action)
    }
  }
}

@ApiStatus.Internal
internal fun createLinuxWebKitWebViewEngine(parentScope: CoroutineScope, backend: LinuxWebKitBackend): LinuxWebKitWebViewEngine {
  return LinuxWebKitWebViewEngine(parentScope, backend)
}
