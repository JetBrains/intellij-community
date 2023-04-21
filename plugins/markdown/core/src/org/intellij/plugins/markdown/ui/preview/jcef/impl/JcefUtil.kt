package org.intellij.plugins.markdown.ui.preview.jcef.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefRequestHandler
import org.intellij.lang.annotations.Language

internal fun JBCefBrowser.executeJavaScript(@Language("JavaScript") code: String) {
  cefBrowser.executeJavaScript(code, null, 0)
}

internal fun JBCefClient.addRequestHandler(handler: CefRequestHandler, browser: CefBrowser, parentDisposable: Disposable) {
  Disposer.register(parentDisposable) { removeRequestHandler(handler, browser) }
  addRequestHandler(handler, browser)
}

internal fun JBCefClient.addLoadHandler(handler: CefLoadHandler, browser: CefBrowser, parentDisposable: Disposable) {
  Disposer.register(parentDisposable) { removeLoadHandler(handler, browser) }
  addLoadHandler(handler, browser)
}
