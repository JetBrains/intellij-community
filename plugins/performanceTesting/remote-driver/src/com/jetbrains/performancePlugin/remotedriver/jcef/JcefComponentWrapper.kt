package com.jetbrains.performancePlugin.remotedriver.jcef

import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Component
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.coroutines.resumeWithException

private const val JB_BROWSER_KEY = "JBCefBrowser.instance"

@Suppress("unused")
internal class JcefComponentWrapper(private val component: Component) {
  private val jbCefBrowser = findBrowser()
  private val jsExecutor = JsExecutor(jbCefBrowser)

  fun hasDocument() = jbCefBrowser.cefBrowser.hasDocument()

  fun getUrl(): String = jbCefBrowser.cefBrowser.url

  fun runJs(js: String) = jsExecutor.runJs(js)

  /**
   * Executes the provided JavaScript code in the embedded browser and returns the result. The code must be able to fit in JBCefJSQuery.
   * Normally, it should be a one-line function call.
   *
   * @param js the JavaScript code to execute
   * @param executeTimeoutMs the maximum time to wait for the JavaScript code to execute
   * @return the result of the JavaScript code execution
   * @throws IllegalStateException if no result is received from the script within the specified timeout
   */
  @Suppress("SSBasedInspection")
  fun callJs(js: String, executeTimeoutMs: Long): String = runBlocking { jsExecutor.callJs(js, executeTimeoutMs) }

  /**
   * Finds the JBCefBrowserBase component associated with the given component.
   *
   * @return the JBCefBrowserBase component
   * @throws IllegalArgumentException if the JBCefBrowserBase component cannot be found
   */
  private fun findBrowser(): JBCefBrowserBase {
    if (component !is JComponent) throw IllegalStateException("$component is not JComponent")
    var jbCefBrowser: Any? = null
    var currentComponent: JComponent? = component
    while (currentComponent != null && jbCefBrowser == null) {
      jbCefBrowser = currentComponent.getClientProperty(JB_BROWSER_KEY)
      currentComponent = currentComponent.parent as JComponent
    }
    check(jbCefBrowser != null) {
      "Failed to retrieve jbCefBrowser from $component"
    }
    check(jbCefBrowser is JBCefBrowserBase) {
      "$jbCefBrowser is not JBCefBrowser"
    }
    return jbCefBrowser
  }

  private class JsExecutor(jbCefBrowser: JBCefBrowserBase) {
    private val cefBrowser = jbCefBrowser.cefBrowser
    private var callback: Consumer<String>? = null

    private val jsResultQuery = JBCefJSQuery.create(jbCefBrowser).apply {
      addHandler {
        callback?.accept(it)
        null
      }
    }

    private val mutex = Mutex()

    fun runJs(js: String) {
      cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
    }

    suspend fun callJs(js: String, executeTimeoutMs: Long = 3000): String = mutex.withLock {
      withTimeout(executeTimeoutMs) {
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation {
            callback = null
            continuation.resumeWithException(IllegalStateException("""
            |No result from script '$js' in embedded browser in ${executeTimeoutMs}ms.
            |Check logs in the browsers devTools(`ide.browser.jcef.contextMenu.devTools.enabled` key in the Registry...)""".trimMargin()))
          }
          callback = Consumer<String> {
            continuation.resumeWith(Result.success(it))
            callback = null
          }
          cefBrowser.executeJavaScript(jsResultQuery.inject(js), cefBrowser.url, 0)
        }
      }
    }
  }
}