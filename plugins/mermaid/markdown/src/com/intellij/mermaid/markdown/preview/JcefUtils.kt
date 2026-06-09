// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.suspendCancellableCoroutine
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.lang.annotations.Language
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun JBCefBrowser.executeJavaScript(@Language("JavaScript") code: String) {
  cefBrowser.executeJavaScript(code, null, 0)
}

internal fun JBCefClient.addLoadHandler(handler: CefLoadHandler, browser: CefBrowser, parentDisposable: Disposable) {
  Disposer.register(parentDisposable) { removeLoadHandler(handler, browser) }
  addLoadHandler(handler, browser)
}

internal class LoadErrorException(
  code: CefLoadHandler.ErrorCode,
  text: String,
  url: String
): IllegalStateException("Failed to load $url:\n$code: $text")

/**
 * Needed only for testing purposes.
 */
abstract class WaitForLoadHandlerAdapter: CefLoadHandlerAdapter()

internal suspend fun JBCefBrowser.waitForLoad(content: JBCefBrowser.() -> Unit) {
  var handlerReference: CefLoadHandler? = null
  suspendCancellableCoroutine { continuation ->
    val handler = object: WaitForLoadHandlerAdapter() {
      @Volatile
      private var handlerWasCalled = false

      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (handlerWasCalled) {
          return
        }
        handlerWasCalled = true
        continuation.resume(Unit)
      }

      override fun onLoadError(
        browser: CefBrowser,
        frame: CefFrame,
        errorCode: CefLoadHandler.ErrorCode,
        errorText: String,
        failedUrl: String
      ) {
        if (handlerWasCalled) {
          return
        }
        handlerWasCalled = true
        continuation.resumeWithException(LoadErrorException(errorCode, errorText, failedUrl))
      }
    }
    handlerReference = handler
    continuation.invokeOnCancellation {
      jbCefClient.removeLoadHandler(handler, cefBrowser)
    }
    jbCefClient.addLoadHandler(handler, cefBrowser)
    content.invoke(this)
  }
  handlerReference?.let { jbCefClient.removeLoadHandler(it, cefBrowser) }
}

suspend fun JBCefBrowser.waitForPageLoad(url: String) {
  waitForLoad {
    loadURL(url)
  }
}

fun queryHandler(handler: (String?) -> Unit): (String?) -> JBCefJSQuery.Response? {
  return {
    handler.invoke(it)
    JBCefJSQuery.Response("")
  }
}

fun JBCefJSQuery.addVoidHandler(handler: (String?) -> Unit): (String?) -> JBCefJSQuery.Response? {
  @Suppress("NAME_SHADOWING")
  val handler = queryHandler(handler)
  addHandler(handler)
  return handler
}
