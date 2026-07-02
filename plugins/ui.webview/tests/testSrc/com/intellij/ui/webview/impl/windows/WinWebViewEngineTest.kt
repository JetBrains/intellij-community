// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.ui.webview.impl.engine.WebViewScript
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.EnumSet
import kotlin.coroutines.CoroutineContext

internal class WinWebViewEngineTest {

  @Test
  fun nativeDiagnosticLevelsMapToLoggerLevels() {
    val factory = Logger.getFactory() as? TestLoggerFactory
    assertNotNull(factory, "WinWebViewEngineTest expects TestLoggerFactory")
    val marker = "win-webview2-diagnostic-${System.nanoTime()}"
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = createActiveEngine(scope, bridge)

    val logged = collectWarningsAndErrors {
      WebViewLogger.LOG.setLevel(LogLevel.TRACE)
      try {
        runInEdtAndWait {
          bridge.callbacks.onNativeDiagnostic(0, "$marker-trace", "trace message", "trace=data")
          bridge.callbacks.onNativeDiagnostic(1, "$marker-debug", "debug message", "debug=data")
          bridge.callbacks.onNativeDiagnostic(2, "$marker-info", "info message", "info=data")
          bridge.callbacks.onNativeDiagnostic(3, "$marker-warn", "warn message", "warn=data")
          bridge.callbacks.onNativeDiagnostic(4, "$marker-error", "error message", "error=data")
        }
      }
      finally {
        WebViewLogger.LOG.setLevel(LogLevel.INFO)
      }
    }

    try {
      val buffer = factory!!.toBuffer()
      assertTrue(buffer.contains("FINER") && buffer.contains("$marker-trace"), buffer)
      assertTrue(buffer.contains("FINE") && buffer.contains("$marker-debug"), buffer)
      assertTrue(buffer.contains("INFO") && buffer.contains("$marker-info"), buffer)
      assertTrue(logged.warnings.any { it.contains("$marker-warn") }, logged.warnings.toString())
      assertTrue(logged.errors.any { it.contains("$marker-error") }, logged.errors.toString())
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  @Test
  fun fatalProcessFailureLogsErrorRecreatesAndReplaysState() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = createActiveEngine(scope, bridge, parentHwnd = 42L)

    val logged = collectWarningsAndErrors {
      runInEdtAndWait {
        engine.setHidden(true)
      }
      runBlocking { engine.loadHtml("<html>last</html>", null) }
      runInEdtAndWait {
        bridge.callbacks.onNativeDiagnostic(4, "process-failed.fatal", "render process crashed", "exitCode=1")
        bridge.callbacks.onCreated(bridge.createdHandles.last())
      }
    }

    try {
      assertTrue(logged.errors.any { it.contains("process-failed.fatal") }, logged.errors.toString())
      assertEquals(listOf(42L, 42L), bridge.createParentHwnds)
      assertEquals(listOf(1L), bridge.destroyedHandles)
      assertTrue(bridge.htmlLoads.any { it.handle == 2L && it.html == "<html>last</html>" }, bridge.htmlLoads.toString())
      assertTrue(bridge.bounds.any { it.handle == 2L && it.bounds == Bounds(10, 20, 300, 200, 1.5) }, bridge.bounds.toString())
      assertTrue(bridge.visibility.any { it.handle == 2L && !it.visible }, bridge.visibility.toString())
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  @Test
  fun createPassesDocumentStartScriptToNativeBridge() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = WinWebViewEngine(
      scope,
      bridge,
      debugName = "test",
      documentStartScripts = listOf(WebViewScript("first"), WebViewScript("second")),
      webViewDispatcher = SyncDispatcher,
    )
    try {
      runInEdtAndWait {
        engine.attachToParent(100L, 10, 20, 300, 200, 1.5)
      }

      assertEquals(listOf("first\n;\nsecond"), bridge.documentStartScripts)
      runInEdtAndWait { bridge.callbacks.onCreated(bridge.createdHandles.single()) }
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  @Test
  fun secondRenderUnresponsiveDiagnosticRecreatesEngine() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = createActiveEngine(scope, bridge)

    val logged = collectWarningsAndErrors {
      runInEdtAndWait {
        bridge.callbacks.onNativeDiagnostic(4, "process-failed.unresponsive", "render process is unresponsive", "count=1")
        assertEquals(listOf(1L), bridge.createdHandles)

        bridge.callbacks.onNativeDiagnostic(4, "process-failed.unresponsive", "render process is unresponsive", "count=2")
        bridge.callbacks.onCreated(bridge.createdHandles.last())
      }
    }

    try {
      assertTrue(logged.errors.any { it.contains("process-failed.unresponsive") }, logged.errors.toString())
      assertEquals(listOf(1L, 2L), bridge.createdHandles)
      assertEquals(listOf(1L), bridge.destroyedHandles)
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  @Test
  fun recoveryLimitLogsErrorAndClosesEngine() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = createActiveEngine(scope, bridge)

    val logged = collectWarningsAndErrors {
      runInEdtAndWait {
        bridge.callbacks.onNativeDiagnostic(4, "process-failed.fatal", "first crash", "")
        bridge.callbacks.onCreated(bridge.createdHandles.last())
        bridge.callbacks.onNativeDiagnostic(4, "process-failed.fatal", "second crash", "")
        bridge.callbacks.onCreated(bridge.createdHandles.last())
        bridge.callbacks.onNativeDiagnostic(4, "process-failed.fatal", "third crash", "")
      }
    }

    try {
      assertEquals(listOf(1L, 2L, 3L), bridge.createdHandles)
      assertEquals(listOf(1L, 2L, 3L), bridge.destroyedHandles)
      assertTrue(logged.errors.any { it.contains("WebView2 engine closed after fatal native failure") }, logged.errors.toString())
      assertTrue(logged.errors.any { it.contains("third crash") }, logged.errors.toString())
      assertNull(runBlocking { engine.evaluateJavaScript("1") })
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  @Test
  fun closeDestroysHandleEvenWhenDispatchedTasksAreDelayed() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val dispatcher = QueuingDispatcher()
    val engine = WinWebViewEngine(scope, bridge, debugName = "test", webViewDispatcher = dispatcher)
    runInEdtAndWait {
      engine.attachToParent(100L, 10, 20, 300, 200, 1.5)
    }
    dispatcher.drain()
    runInEdtAndWait { bridge.callbacks.onCreated(bridge.createdHandles.last()) }

    runBlocking { engine.close() }
    dispatcher.drain()

    assertEquals(listOf(1L), bridge.destroyedHandles)
    scope.cancel()
  }

  @Test
  fun setBoundsSpamCoalescesIntoSingleApply() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val dispatcher = QueuingDispatcher()
    val engine = WinWebViewEngine(scope, bridge, debugName = "test", webViewDispatcher = dispatcher)
    try {
      runInEdtAndWait { engine.attachToParent(100L, 10, 20, 300, 200, 1.5) }
      dispatcher.drain()
      runInEdtAndWait { bridge.callbacks.onCreated(bridge.createdHandles.last()) }

      bridge.bounds.clear()
      runInEdtAndWait {
        repeat(10) { i ->
          engine.setBounds(i * 5, i * 5, 100 + i, 100 + i, 1.0)
        }
      }
      assertEquals(1, dispatcher.pendingCount(), "expected setBounds to coalesce into a single queued task")
      dispatcher.drain()
      assertEquals(1, bridge.bounds.size)
      assertEquals(Bounds(45, 45, 109, 109, 1.0), bridge.bounds[0].bounds)
    }
    finally {
      runBlocking { engine.close() }
      dispatcher.drain()
      scope.cancel()
    }
  }

  @Test
  fun attachToParentRespectsLatestParentOnce() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val dispatcher = QueuingDispatcher()
    val engine = WinWebViewEngine(scope, bridge, debugName = "test", webViewDispatcher = dispatcher)
    try {
      runInEdtAndWait {
        engine.attachToParent(100L, 10, 20, 300, 200, 1.5)
        engine.attachToParent(200L, 30, 40, 500, 400, 2.0)
      }
      dispatcher.drain()
      runInEdtAndWait { bridge.callbacks.onCreated(bridge.createdHandles.last()) }
      dispatcher.drain()

      assertEquals(listOf(200L), bridge.createParentHwnds,
                   "performCreate should pick up the latest parent set before it runs")
      assertEquals(emptyList<Long>(), bridge.attachParents,
                   "no follow-up bridge.attachToParent expected when create already used latest parent")
    }
    finally {
      runBlocking { engine.close() }
      dispatcher.drain()
      scope.cancel()
    }
  }

  @Test
  fun createAppliesInitialBoundsBeforeFirstVisibilityAndKeepsHiddenUntilCreated() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = WinWebViewEngine(scope, bridge, debugName = "test", webViewDispatcher = SyncDispatcher)
    try {
      runInEdtAndWait { engine.attachToParent(100L, 10, 20, 300, 200, 1.5) }
      runInEdtAndWait { bridge.callbacks.onCreated(bridge.createdHandles.single()) }

      assertEquals(
        listOf("create:100", "bounds:1:10:20:300:200:1.5", "visible:1:false", "visible:1:true"),
        bridge.callOrder,
      )
    }
    finally {
      runBlocking { engine.close() }
      scope.cancel()
    }
  }

  @Test
  fun transferToJsWorksWhileWebViewIsHidden() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = createActiveEngine(scope, bridge)
    try {
      runInEdtAndWait { engine.setHidden(true) }
      runBlocking { engine.transferToJs("{\"jsonrpc\":\"2.0\",\"method\":\"markdown.preview/contentChanged\"}") }

      assertEquals(
        listOf(JsTransfer(1L, "{\"jsonrpc\":\"2.0\",\"method\":\"markdown.preview/contentChanged\"}")),
        bridge.jsTransfers,
      )
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  @Test
  fun transientFocusFailuresDoNotEscapeDispatcherTask() {
    val bridge = FakeWinWebView2Bridge()
    val scope = testScope()
    val engine = createActiveEngine(scope, bridge)
    try {
      bridge.focusFailure = IllegalStateException("focus failed")
      bridge.clearFocusFailure = IllegalStateException("clear focus failed")

      runInEdtAndWait {
        engine.requestFocus()
        engine.clearFocus()
      }

      assertEquals(listOf(1L), bridge.focusedHandles)
      assertEquals(listOf(1L), bridge.clearFocusedHandles)
    }
    finally {
      closeEngine(engine, scope)
    }
  }

  private fun createActiveEngine(
    scope: CoroutineScope,
    bridge: FakeWinWebView2Bridge,
    parentHwnd: Long = 100L,
  ): WinWebViewEngine {
    val engine = WinWebViewEngine(
      scope,
      bridge,
      debugName = "test",
      webViewDispatcher = SyncDispatcher,
    )
    runInEdtAndWait {
      engine.attachToParent(parentHwnd, 10, 20, 300, 200, 1.5)
      bridge.callbacks.onCreated(bridge.createdHandles.last())
    }
    return engine
  }

  private fun testScope(): CoroutineScope {
    @Suppress("RAW_SCOPE_CREATION") // Test scope has no parent fixture scope.
    return CoroutineScope(SupervisorJob())
  }

  /**
   * Runs every dispatched [Runnable] inline on the calling thread. The engine
   * uses `dispatcher.dispatch(...)` directly (not `launch`), so we cannot rely
   * on `Dispatchers.Unconfined` here — its `dispatch` throws.
   */
  private object SyncDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      block.run()
    }
  }

  /** Queues every dispatched runnable; runs them only on explicit [drain]. */
  private class QueuingDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      synchronized(queue) { queue.addLast(block) }
    }

    fun pendingCount(): Int = synchronized(queue) { queue.size }

    fun drain() {
      while (true) {
        val next = synchronized(queue) { queue.removeFirstOrNull() } ?: return
        next.run()
      }
    }
  }

  private fun closeEngine(engine: WinWebViewEngine, scope: CoroutineScope) {
    runBlocking { engine.close() }
    runInEdtAndWait {}
    scope.cancel()
  }

  private fun collectWarningsAndErrors(action: () -> Unit): LoggedMessages {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val token = LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<LoggedErrorProcessor.Action> {
        errors.add(message)
        return EnumSet.noneOf(LoggedErrorProcessor.Action::class.java)
      }

      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warnings.add(message)
        return false
      }
    })
    try {
      action()
    }
    finally {
      token.finish()
    }
    return LoggedMessages(warnings, errors)
  }

  private data class LoggedMessages(
    val warnings: List<String>,
    val errors: List<String>,
  )

  private data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val scale: Double,
  )

  private data class HtmlLoad(
    val handle: Long,
    val html: String,
    val baseUrl: String?,
  )

  private data class Visibility(
    val handle: Long,
    val visible: Boolean,
  )

  private data class JsTransfer(
    val handle: Long,
    val rawJson: String,
  )

  private class FakeWinWebView2Bridge : WinWebView2BridgeApi {
    lateinit var callbacks: WinWebView2Bridge.Callbacks
      private set

    val createdHandles = mutableListOf<Long>()
    val createParentHwnds = mutableListOf<Long>()
    val attachParents = mutableListOf<Long>()
    val destroyedHandles = mutableListOf<Long>()
    val bounds = mutableListOf<BoundsRecord>()
    val visibility = mutableListOf<Visibility>()
    val htmlLoads = mutableListOf<HtmlLoad>()
    val jsTransfers = mutableListOf<JsTransfer>()
    val documentStartScripts = mutableListOf<String>()
    val focusedHandles = mutableListOf<Long>()
    val clearFocusedHandles = mutableListOf<Long>()
    val callOrder = mutableListOf<String>()
    var focusFailure: IllegalStateException? = null
    var clearFocusFailure: IllegalStateException? = null
    private var nextHandle = 1L

    override fun create(parentHwnd: Long, userDataDir: String, documentStartScript: String, callbacks: WinWebView2Bridge.Callbacks): Long {
      this.callbacks = callbacks
      documentStartScripts.add(documentStartScript)
      createParentHwnds.add(parentHwnd)
      callOrder.add("create:$parentHwnd")
      return nextHandle++.also { createdHandles.add(it) }
    }

    override fun destroy(handle: Long) {
      destroyedHandles.add(handle)
    }

    override fun attachToParent(handle: Long, parentHwnd: Long) {
      attachParents.add(parentHwnd)
    }

    override fun detachFromParent(handle: Long) {
    }

    override fun setBounds(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double) {
      bounds.add(BoundsRecord(handle, Bounds(x, y, width, height, scale)))
      callOrder.add("bounds:$handle:$x:$y:$width:$height:$scale")
    }

    override fun setVisible(handle: Long, visible: Boolean) {
      visibility.add(Visibility(handle, visible))
      callOrder.add("visible:$handle:$visible")
    }

    override fun focus(handle: Long) {
      focusedHandles.add(handle)
      focusFailure?.let { throw it }
    }

    override fun clearFocus(handle: Long) {
      clearFocusedHandles.add(handle)
      clearFocusFailure?.let { throw it }
    }

    override fun loadUrl(handle: Long, url: String) {
    }

    override fun loadHtml(handle: Long, html: String, baseUrl: String?) {
      htmlLoads.add(HtmlLoad(handle, html, baseUrl))
    }

    override fun evaluateJavaScript(handle: Long, evalId: Long, script: String) {
    }

    override fun transferToJs(handle: Long, rawJson: String) {
      jsTransfers.add(JsTransfer(handle, rawJson))
    }
  }

  private data class BoundsRecord(
    val handle: Long,
    val bounds: Bounds,
  )
}
