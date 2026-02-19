// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.jcef

import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.awt.Component
import java.util.function.Function
import javax.swing.JComponent

private const val JB_BROWSER_KEY = "JBCefBrowser.instance"

@Suppress("unused")
internal class JcefComponentWrapper(private val component: Component) {
  private val jbCefBrowser = findBrowser()
  private val jsExecutor = JsExecutor(jbCefBrowser)

  fun hasDocument() = jbCefBrowser.cefBrowser.hasDocument()

  fun getUrl(): String = jbCefBrowser.cefBrowser.url

  fun runJs(js: String) = jsExecutor.runJs(js)

  fun isLoading() = jbCefBrowser.cefBrowser.isLoading

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

  private class JsExecutor(private val jbCefBrowser: JBCefBrowserBase) {

    fun runJs(js: String) {
      jbCefBrowser.cefBrowser.run {
        executeJavaScript(js, url, 0)
      }
    }

    suspend fun callJs(js: String, executeTimeoutMs: Long = 3000): String =
      withTimeout(executeTimeoutMs) {
        suspendCancellableCoroutine { continuation ->
          val jsResultQuery = JBCefJSQuery.create(jbCefBrowser)
          coroutineContext.job.invokeOnCompletion {
            Disposer.dispose(jsResultQuery)
          }

          val handler = object : Function<String, JBCefJSQuery.Response?> {
            override fun apply(result: String): JBCefJSQuery.Response? {
              jsResultQuery.removeHandler(this)
              continuation.resumeWith(Result.success(result))
              return null
            }
          }

          jsResultQuery.addHandler(handler)

          continuation.invokeOnCancellation {
            jsResultQuery.removeHandler(handler)
          }

          runJs(jsResultQuery.inject(js))
        }
      }
  }
}