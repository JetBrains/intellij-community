package com.intellij.mermaid.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.lang.annotations.Language
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

internal suspend fun JBCefBrowser.waitForLoad(content: JBCefBrowser.() -> Unit) {
  return suspendCoroutine { continuation ->
    val handler = object: CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        continuation.resume(Unit)
      }

      override fun onLoadError(
        browser: CefBrowser,
        frame: CefFrame,
        errorCode: CefLoadHandler.ErrorCode,
        errorText: String,
        failedUrl: String
      ) {
        continuation.resumeWithException(LoadErrorException(errorCode, errorText, failedUrl))
      }
    }
    jbCefClient.addLoadHandler(handler, cefBrowser)
    content.invoke(this)
  }
}

internal suspend fun JBCefBrowser.waitForPageLoad(url: String) {
  waitForLoad {
    loadURL(url)
  }
}
