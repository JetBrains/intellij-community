package com.jetbrains.performancePlugin.remotedriver.jcef

import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.Component
import javax.swing.JComponent

private const val JB_BROWSER_KEY = "JBCefBrowser.instance"

@Suppress("unused")
internal class JcefComponentWrapper(private val component: Component) {
  private val jbCefBrowser = findBrowser()
  private val jsExecutor = SyncJsExecutor(jbCefBrowser)

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
  fun callJs(js: String, executeTimeoutMs: Long): String = jsExecutor.callJs(js, executeTimeoutMs)

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
    require(jbCefBrowser != null) {
      "Failed to retrieve jbCefBrowser from $component"
    }
    require(jbCefBrowser is JBCefBrowserBase) {
      "$jbCefBrowser is not JBCefBrowser"
    }
    return jbCefBrowser
  }

  private class SyncJsExecutor(jbCefBrowser: JBCefBrowserBase) {
    companion object {
      private const val JS_IN_PROGRESS = "!!%##IN_PROGRESS##%!!"
      private const val EXECUTE_JS_POLL_INTERVAL_MS = 50L
    }

    private val cefBrowser = jbCefBrowser.cefBrowser

    @Volatile
    private var jsResult: String = ""
    private val jsResultQuery = JBCefJSQuery.create(jbCefBrowser).apply {
      addHandler {
        jsResult = it
        null
      }
    }

    fun runJs(js: String) {
      cefBrowser.executeJavaScript(js.toOneLine(), cefBrowser.url, 0)
    }

    fun callJs(js: String, executeTimeoutMs: Long = 3000): String = synchronized(this) {
      cefBrowser.executeJavaScript(jsResultQuery.inject(js), cefBrowser.url, 0)
      jsResult = JS_IN_PROGRESS
      val maxTime = System.currentTimeMillis() + executeTimeoutMs
      while (jsResult == JS_IN_PROGRESS) {
        if (System.currentTimeMillis() > maxTime) {
          throw IllegalStateException("""
            |No result from script '$js' in embedded browser.
            |Check logs in the browsers devTools(`ide.browser.jcef.contextMenu.devTools.enabled` key in the Registry...)""".trimMargin())
        }
        Thread.sleep(EXECUTE_JS_POLL_INTERVAL_MS)
      }
      jsResult
    }

    private fun String.toOneLine(): String = split("\n")
      .map {
        StringBuilder(it.trim()).apply {
          if (isNotEmpty() && endsWith(";").not() && endsWith("{").not() && endsWith("}").not()) {
            append(";")
          }
        }
      }.let { buildString { it.forEach { append("$it ") } } }
  }
}