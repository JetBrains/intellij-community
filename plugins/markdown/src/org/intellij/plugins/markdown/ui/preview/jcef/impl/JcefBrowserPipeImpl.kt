// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.api.client.json.jackson2.JacksonFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * You can check resource/org/intellij/plugins/markdown/ui/preview/jcef/BrowserPipe.js
 * for the browser-side implementation.
 */
internal class JcefBrowserPipeImpl(private val browser: JBCefBrowserBase): BrowserPipe {
  private val query = checkNotNull(JBCefJSQuery.create(browser))
  private val receiveSubscribers = hashMapOf<String, MutableList<BrowserPipe.Handler>>()

  init {
    Disposer.register(this, query)
    query.addHandler(::receiveHandler)
    browser.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        val code = query.inject("raw")
        browser.executeJavaScript("window['$ourBrowserNamespace']['$postToIdeFunctionName'] = raw => $code;", null, 0)
        browser.executeJavaScript("window.dispatchEvent(new Event('IdeReady'));", null, 0)
      }
    }, browser.cefBrowser)
  }

  override fun subscribe(type: String, handler: BrowserPipe.Handler) {
    receiveSubscribers.merge(type, mutableListOf(handler)) { current, _ ->
      current.also { it.add(handler) }
    }
  }

  override fun removeSubscription(type: String, handler: BrowserPipe.Handler) {
    receiveSubscribers[type]?.remove(handler)
    if (receiveSubscribers[type]?.isEmpty() == true) {
      receiveSubscribers.remove(type)
    }
  }

  override fun send(type: String, data: String) {
    val raw = jacksonObjectMapper().writeValueAsString(PackedMessage(type, data))
    logger.debug("Sending message: $raw")
    browser.cefBrowser.executeJavaScript(postToBrowserFunctionCall(raw), null, 0)
  }

  override fun dispose() = Unit

  @ApiStatus.Internal
  data class PackedMessage(
    val type: String,
    val data: String
  )

  private fun parseMessage(raw: String): PackedMessage? {
    try {
      return jacksonObjectMapper().readValue<PackedMessage>(raw).takeIf { it.type.isNotEmpty() }
    } catch (exception: IOException) {
      logger.error(exception)
      return null
    }
  }

  private fun receiveHandler(raw: String?): JBCefJSQuery.Response? {
    val (type, data) = raw?.let(::parseMessage) ?: return null
    callSubscribers(type, data)
    return null
  }

  private fun callSubscribers(type: String, data: String) {
    when (val subscribers = receiveSubscribers[type]) {
      null -> logger.warn("No subscribers for $type!\nAttached data: $data")
      else -> subscribers.forEach { it.messageReceived(data) }
    }
  }

  companion object {
    private val logger = logger<JcefBrowserPipeImpl>()

    private fun postToBrowserFunctionCall(data: String): String {
      return "window.__IntelliJTools.messagePipe.callBrowserListeners($data);"
    }

    private const val ourBrowserNamespace = "__IntelliJTools"
    private const val postToIdeFunctionName = "___jcefMessagePipePostToIdeFunction"
    const val WINDOW_READY_EVENT = "documentReady"
  }
}
